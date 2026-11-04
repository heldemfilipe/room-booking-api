package dev.heldem.reservas.sala.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull @Min(1) @Max(1000) Integer capacidade) {
}
