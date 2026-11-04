package dev.heldem.reservas.sala.dto;

import java.time.Instant;
import java.util.UUID;

import dev.heldem.reservas.sala.Sala;

// a entidade JPA nunca sai da API
public record SalaResponse(UUID id, String nome, int capacidade, Instant criadaEm) {

    public static SalaResponse de(Sala sala) {
        return new SalaResponse(sala.getId(), sala.getNome(), sala.getCapacidade(), sala.getCriadaEm());
    }
}
