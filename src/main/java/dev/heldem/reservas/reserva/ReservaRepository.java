package dev.heldem.reservas.reserva;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, UUID>, JpaSpecificationExecutor<Reserva> {

    boolean existsBySalaId(UUID salaId);

    /**
     * Existe reserva ATIVA da sala que se sobrepõe a [inicio, fim)? Intervalos que só se tocam (fim == início) não se
     * sobrepõem, e uma reserva CANCELADA libera o horário.
     */
    @Query("""
            select count(r) > 0 from Reserva r
            where r.sala.id = :salaId
              and r.status = dev.heldem.reservas.reserva.StatusReserva.ATIVA
              and r.inicio < :fim and r.fim > :inicio
            """)
    boolean existeSobreposicao(@Param("salaId") UUID salaId, @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /**
     * A listagem devolve o nome da sala de cada reserva. Com a sala LAZY, isso era uma consulta por sala (N+1: 52
     * consultas para 50 reservas em 50 salas). O grafo busca a sala junto, com um join, na mesma consulta da página; a
     * consulta de contagem não leva o join.
     */
    @Override
    @EntityGraph(attributePaths = "sala")
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);
}
