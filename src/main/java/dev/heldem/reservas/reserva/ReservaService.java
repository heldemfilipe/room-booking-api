package dev.heldem.reservas.reserva;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.comum.AcessoNegadoException;
import dev.heldem.reservas.comum.ChaveReutilizadaException;
import dev.heldem.reservas.comum.ConflitoException;
import dev.heldem.reservas.comum.Datas;
import dev.heldem.reservas.comum.EntradaInvalidaException;
import dev.heldem.reservas.comum.Pagina;
import dev.heldem.reservas.comum.RecursoNaoEncontradoException;
import dev.heldem.reservas.reserva.dto.CriarReservaRequest;
import dev.heldem.reservas.reserva.idempotencia.ChaveIdempotencia;
import dev.heldem.reservas.reserva.idempotencia.ChaveIdempotenciaRepository;
import dev.heldem.reservas.reserva.dto.ReservaResponse;
import dev.heldem.reservas.sala.Sala;
import dev.heldem.reservas.sala.SalaService;

// Os métodos devolvem DTO porque o mapeamento precisa acontecer dentro da transação (a sala da reserva é LAZY).
@Service
public class ReservaService {

    // SQLStates do PostgreSQL: 23P01 = exclusion_violation (a constraint recusou); 40P01 = deadlock_detected
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String DEADLOCK_DETECTED = "40P01";

    // lista estável: por início, com o id como desempate
    private static final Sort ORDEM = Sort.by("inicio", "id");

    private static final int TAMANHO_MAXIMO_DA_CHAVE = 100;

    private final ReservaRepository repository;
    private final ChaveIdempotenciaRepository chaves;
    private final SalaService salaService;
    private final Clock clock;
    private final MetricasDeReservas metricas;

    public ReservaService(ReservaRepository repository, ChaveIdempotenciaRepository chaves, SalaService salaService,
            Clock clock, MetricasDeReservas metricas) {
        this.metricas = metricas;
        this.repository = repository;
        this.chaves = chaves;
        this.salaService = salaService;
        this.clock = clock;
    }

    /** A reserva criada e se ela veio de um replay da mesma Idempotency-Key (então nada novo foi gravado). */
    public record ResultadoCriacao(ReservaResponse reserva, boolean repetida) {
    }

    /**
     * POST /reservas com a chave de idempotência opcional. Sem chave, é a criação normal. Com chave:
     * <ol>
     *   <li>a chave é registrada PRIMEIRO, na mesma transação, com ON CONFLICT DO NOTHING: duas requisições simultâneas
     *       com a mesma chave não passam juntas, porque a segunda espera a primeira confirmar;</li>
     *   <li>se a chave já existia e o corpo é o mesmo, devolve a reserva criada antes (replay), sem gravar nada;</li>
     *   <li>se existia com outro corpo, 422;</li>
     *   <li>se a criação falha (409, 404...), a transação desfaz tudo, inclusive a chave: só respostas de sucesso são
     *       memorizadas, e o cliente pode tentar de novo com a mesma chave.</li>
     * </ol>
     */
    @Transactional
    public ResultadoCriacao criar(String usuarioId, String chave, CriarReservaRequest request) {
        if (chave == null) {
            return new ResultadoCriacao(criar(usuarioId, request), false);
        }
        if (chave.isBlank() || chave.length() > TAMANHO_MAXIMO_DA_CHAVE) {
            throw new EntradaInvalidaException(
                    "Idempotency-Key deve ter de 1 a " + TAMANHO_MAXIMO_DA_CHAVE + " caracteres.");
        }
        String hash = hashDa(request);
        int criadas = chaves.inserirSeAusente(UUID.randomUUID(), usuarioId, chave, hash, Datas.agora(clock));
        if (criadas == 0) {
            ChaveIdempotencia existente = chaves.findByUsuarioIdAndChave(usuarioId, chave).orElseThrow();
            if (!existente.getHashRequisicao().equals(hash)) {
                throw new ChaveReutilizadaException();
            }
            if (existente.getReservaId() == null) {
                // Não deveria existir: a chave e a reserva são gravadas na mesma transação. Se aparecer (por exemplo, uma
                // linha mexida à mão), é melhor um 409 claro do que um erro de servidor.
                throw new ConflitoException("A requisição com esta Idempotency-Key não terminou. Tente de novo.");
            }
            // Devolve a reserva como ela está agora (se foi cancelada depois, o replay mostra CANCELADA).
            return new ResultadoCriacao(buscar(existente.getReservaId()), true);
        }
        ReservaResponse criada = criar(usuarioId, request);
        chaves.definirReserva(usuarioId, chave, criada.id());
        return new ResultadoCriacao(criada, false);
    }

    /** SHA-256 (hex) do que identifica a requisição: sala, início e fim, já na precisão do banco. */
    private static String hashDa(CriarReservaRequest request) {
        String conteudo = request.salaId() + "|" + Datas.paraOBanco(request.inicio()) + "|"
                + Datas.paraOBanco(request.fim());
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 não está disponível", ex);
        }
    }

    /**
     * Reserva dupla, em duas camadas (veja o experimento no README):
     * <ol>
     *   <li><b>A garantia é do BANCO:</b> a exclusion constraint ex_reservas_sem_sobreposicao (migration V3) recusa
     *       qualquer reserva ativa que se sobreponha a outra da mesma sala, venha de onde vier, inclusive de SQL direto.</li>
     *   <li><b>A fila é do lock pessimista:</b> a primeira coisa que a transação faz é trancar a linha da sala (SELECT ...
     *       FOR UPDATE), então as reservas da MESMA sala são feitas uma de cada vez. Sozinha, a constraint sob disputa
     *       pesada pelo mesmo horário gerava deadlocks em cascata (cauda de segundos); com o lock, quem espera já enxerga a
     *       reserva da frente na consulta de sobreposição e recebe 409 sem erro de banco nenhum.</li>
     * </ol>
     * A consulta de sobreposição dá a resposta 409 clara no caso comum; se ainda assim a constraint recusar (ou o banco
     * derrubar por deadlock), isso também vira 409, nunca 500.
     */
    @Transactional
    public ReservaResponse criar(String usuarioId, CriarReservaRequest request) {
        Sala sala = salaService.buscarParaReservar(request.salaId()); // 404 se a sala não existir; tranca a linha
        Instant inicio = Datas.paraOBanco(request.inicio());
        Instant fim = Datas.paraOBanco(request.fim());
        if (repository.existeSobreposicao(sala.getId(), inicio, fim)) {
            throw salaOcupada(sala);
        }
        var reserva = new Reserva(sala, usuarioId, inicio, fim, Datas.agora(clock));
        try {
            // saveAndFlush: a violação da constraint precisa estourar aqui, dentro do try, e não no commit
            return ReservaResponse.de(repository.saveAndFlush(reserva));
        } catch (DataAccessException ex) {
            String sqlState = sqlState(ex);
            if (EXCLUSION_VIOLATION.equals(sqlState)) {
                throw salaOcupada(sala);
            }
            if (DEADLOCK_DETECTED.equals(sqlState)) {
                // Achado do experimento: com muitas transações disputando o MESMO horário, o PostgreSQL pode deixar duas
                // esperando uma pela outra no índice da constraint e derruba a(s) perdedora(s) com deadlock (depois de
                // deadlock_timeout, 1 s). O resultado continua correto (só uma reserva entra); a perdedora é um conflito
                // de concorrência, e não um erro do servidor.
                metricas.conflito();
                throw new ConflitoException("Outra reserva da sala '" + sala.getNome()
                        + "' estava sendo feita ao mesmo tempo e levou a melhor. Consulte a disponibilidade e tente de novo.");
            }
            throw ex;
        }
    }

    /** O SQLState do erro do banco, procurado na cadeia de causas (sem depender da classe do driver). */
    private static String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private ConflitoException salaOcupada(Sala sala) {
        metricas.conflito();
        return new ConflitoException("A sala '" + sala.getNome() + "' já está reservada em parte desse horário.");
    }

    @Transactional(readOnly = true)
    public ReservaResponse buscar(UUID id) {
        return ReservaResponse.de(obter(id));
    }

    /** Reservas que se sobrepõem a [de, ate), da sala informada (todos os filtros são opcionais). */
    @Transactional(readOnly = true)
    public Pagina<ReservaResponse> listar(UUID salaId, Instant de, Instant ate, int pagina, int tamanho) {
        if (de != null && ate != null && !de.isBefore(ate)) {
            throw new EntradaInvalidaException("'de' deve ser antes de 'ate'.");
        }
        Specification<Reserva> filtro = Specification.allOf(
                ReservaSpecs.daSala(salaId), ReservaSpecs.terminaDepoisDe(de), ReservaSpecs.comecaAntesDe(ate));
        Page<Reserva> resultado = repository.findAll(filtro, PageRequest.of(pagina, tamanho, ORDEM));
        return Pagina.de(resultado, ReservaResponse::de);
    }

    /**
     * DELETE /reservas/{id}: cancela (muda o status) e é idempotente. Só quem criou a reserva cancela: o dono vem do
     * token (nunca do corpo ou do caminho) e é comparado com o da reserva; ADMIN não é exceção (docs: Decisões).
     */
    @Transactional
    public void cancelar(UUID id, String usuarioId) {
        Reserva reserva = obter(id); // 404 antes de 403
        if (!reserva.getUsuarioId().equals(usuarioId)) {
            throw new AcessoNegadoException("Só quem criou a reserva pode cancelá-la.");
        }
        reserva.cancelar();
    }

    private Reserva obter(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva " + id + " não encontrada."));
    }
}
