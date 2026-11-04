package dev.heldem.reservas.reserva;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de GET /reservas. Cada um devolve "sem restrição" (conjunction) quando o parâmetro não veio: o
 * Specification.allOf não aceita {@code null}, e assim só os filtros presentes restringem a consulta.
 */
final class ReservaSpecs {

    private static final Specification<Reserva> SEM_RESTRICAO = (reserva, query, cb) -> cb.conjunction();

    private ReservaSpecs() {
    }

    static Specification<Reserva> daSala(UUID salaId) {
        return salaId == null ? SEM_RESTRICAO : (reserva, query, cb) -> cb.equal(reserva.get("sala").get("id"), salaId);
    }

    /** Sobreposição com [de, ate): a reserva termina depois de {@code de}... */
    static Specification<Reserva> terminaDepoisDe(Instant de) {
        return de == null ? SEM_RESTRICAO : (reserva, query, cb) -> cb.greaterThan(reserva.get("fim"), de);
    }

    /** ...e começa antes de {@code ate}. Intervalos que só se tocam (fim == início) não se sobrepõem. */
    static Specification<Reserva> comecaAntesDe(Instant ate) {
        return ate == null ? SEM_RESTRICAO : (reserva, query, cb) -> cb.lessThan(reserva.get("inicio"), ate);
    }
}
