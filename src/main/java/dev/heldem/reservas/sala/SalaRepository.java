package dev.heldem.reservas.sala;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SalaRepository extends JpaRepository<Sala, UUID> {

    /** O índice uk_salas_nome é sobre lower(nome): a comparação ignora maiúsculas. */
    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    List<Sala> findAll(Sort ordem);

    /**
     * Carrega a sala para criar uma reserva e MARCA a versão para subir no commit (OPTIMISTIC_FORCE_INCREMENT), mesmo que
     * a sala em si não mude. Se outra transação confirmou uma reserva nesse meio tempo, o UPDATE da versão encontra uma
     * versão diferente e falha com ObjectOptimisticLockingFailureException.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select s from Sala s where s.id = :id")
    Optional<Sala> buscarParaReservar(@Param("id") UUID id);
}
