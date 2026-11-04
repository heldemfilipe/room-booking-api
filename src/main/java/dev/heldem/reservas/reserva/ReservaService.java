package dev.heldem.reservas.reserva;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.comum.AcessoNegadoException;
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
     * Nesta etapa a reserva ainda é criada SEM checar sobreposição: duas reservas da mesma sala no mesmo horário são
     * aceitas. Isso é proposital e é a Etapa 3 (reproduzir a corrida e fechá-la), não um esquecimento.
     */
    @Transactional
    public ReservaResponse criar(String usuarioId, CriarReservaRequest request) {
        Sala sala = salaService.buscar(request.salaId()); // 404 se a sala não existir
        var reserva = new Reserva(sala, usuarioId, Datas.paraOBanco(request.inicio()),
                Datas.paraOBanco(request.fim()), Datas.agora(clock));
        return ReservaResponse.de(repository.save(reserva));
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
