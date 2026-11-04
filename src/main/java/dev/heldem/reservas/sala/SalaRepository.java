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
     * Carrega a sala para criar uma reserva trancando a linha (SELECT ... FOR UPDATE). Quem chega depois espera aqui até
     * a transação da frente confirmar; ao ser liberada, a consulta de sobreposição já enxerga a reserva dela.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sala s where s.id = :id")
    Optional<Sala> buscarParaReservar(@Param("id") UUID id);
}
