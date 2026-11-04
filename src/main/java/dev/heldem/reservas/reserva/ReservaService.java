package dev.heldem.reservas.reserva;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.comum.AcessoNegadoException;
import dev.heldem.reservas.comum.ConflitoException;
import dev.heldem.reservas.comum.Datas;
import dev.heldem.reservas.comum.EntradaInvalidaException;
import dev.heldem.reservas.comum.Pagina;
import dev.heldem.reservas.comum.RecursoNaoEncontradoException;
import dev.heldem.reservas.reserva.dto.CriarReservaRequest;
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

    private final ReservaRepository repository;
    private final SalaService salaService;
    private final Clock clock;

    public ReservaService(ReservaRepository repository, SalaService salaService, Clock clock) {
        this.repository = repository;
        this.salaService = salaService;
        this.clock = clock;
    }

    /**
     * A garantia de que não há reserva dupla é do BANCO: a exclusion constraint ex_reservas_sem_sobreposicao (migration
     * V3). A consulta de sobreposição abaixo continua, mas só para dar a resposta 409 sem um erro de banco no caso comum;
     * quem fecha a corrida é a constraint, e a violação dela também vira 409, nunca 500.
     */
    @Transactional
    public ReservaResponse criar(String usuarioId, CriarReservaRequest request) {
        Sala sala = salaService.buscar(request.salaId()); // 404 se a sala não existir
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

    private static ConflitoException salaOcupada(Sala sala) {
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
