package dev.heldem.reservas.sala;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaRepository extends JpaRepository<Sala, UUID> {

    /** O índice uk_salas_nome é sobre lower(nome): a comparação ignora maiúsculas. */
    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    List<Sala> findAll(Sort ordem);
}
