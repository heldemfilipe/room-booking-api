package dev.heldem.reservas.reserva.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * O dono da reserva NÃO vem no corpo: é o {@code sub} do token (veja ReservaController). Um {@code usuarioId} enviado no
 * corpo é simplesmente ignorado, então ninguém consegue reservar em nome de outra pessoa.
 */
public record CriarReservaRequest(
        @NotNull UUID salaId,
        @NotNull Instant inicio,
        @NotNull Instant fim) {

    // O banco também recusa (CHECK fim > inicio); aqui a resposta é um 400 com o campo, e não um erro de banco.
    @AssertTrue(message = "fim deve ser depois de inicio")
    public boolean isIntervaloValido() {
        return inicio == null || fim == null || fim.isAfter(inicio);
    }
}
