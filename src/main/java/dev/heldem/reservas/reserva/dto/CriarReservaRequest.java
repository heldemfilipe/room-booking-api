package dev.heldem.reservas.reserva.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O {@code usuarioId} vem no corpo SÓ nesta etapa, enquanto a API não tem autenticação. Na Etapa 2 ele passa a vir do
 * token (o "sub" do JWT), nunca do corpo da requisição.
 */
public record CriarReservaRequest(
        @NotNull UUID salaId,
        @NotBlank @Size(max = 64) String usuarioId,
        @NotNull Instant inicio,
        @NotNull Instant fim) {

    // O banco também recusa (CHECK fim > inicio); aqui a resposta é um 400 com o campo, e não um erro de banco.
    @AssertTrue(message = "fim deve ser depois de inicio")
    public boolean isIntervaloValido() {
        return inicio == null || fim == null || fim.isAfter(inicio);
    }
}
