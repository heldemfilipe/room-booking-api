package dev.heldem.reservas.reserva;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReservaRepository extends JpaRepository<Reserva, UUID>, JpaSpecificationExecutor<Reserva> {

    boolean existsBySalaId(UUID salaId);

    /**
     * A listagem devolve o nome da sala de cada reserva. Com a sala LAZY, isso era uma consulta por sala (N+1: 52
     * consultas para 50 reservas em 50 salas). O grafo busca a sala junto, com um join, na mesma consulta da página; a
     * consulta de contagem não leva o join.
     */
    @Override
    @EntityGraph(attributePaths = "sala")
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);
}
