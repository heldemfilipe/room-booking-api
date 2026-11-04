package dev.heldem.reservas.reserva.dto;

import java.time.Instant;
import java.util.UUID;

import dev.heldem.reservas.reserva.Reserva;
import dev.heldem.reservas.reserva.StatusReserva;

// a entidade JPA nunca sai da API; o nome da sala vem junto (é isso que expõe o N+1 da listagem)
public record ReservaResponse(
        UUID id,
        UUID salaId,
        String salaNome,
        String usuarioId,
        Instant inicio,
        Instant fim,
        StatusReserva status,
        Instant criadaEm) {

    public static ReservaResponse de(Reserva reserva) {
        return new ReservaResponse(reserva.getId(), reserva.getSala().getId(), reserva.getSala().getNome(),
                reserva.getUsuarioId(), reserva.getInicio(), reserva.getFim(), reserva.getStatus(),
                reserva.getCriadaEm());
    }
}
