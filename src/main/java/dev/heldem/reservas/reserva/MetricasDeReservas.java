package dev.heldem.reservas.reserva;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Métricas de negócio das reservas (Etapa 5), as que o dashboard mostra ao lado das técnicas (requisições, erros,
 * latência): quantas reservas foram criadas e quantas tentativas deram conflito (409 por sala já ocupada).
 *
 * <p>No Prometheus: {@code reservas_criadas_total} e {@code reservas_conflitos_total}. O replay de uma
 * Idempotency-Key não conta como reserva criada (nada novo foi gravado).
 */
@Component
public class MetricasDeReservas {

    private final Counter criadas;
    private final Counter conflitos;

    public MetricasDeReservas(MeterRegistry registro) {
        this.criadas = Counter.builder("reservas.criadas")
                .description("Reservas criadas (sem contar o replay de uma Idempotency-Key)").register(registro);
        this.conflitos = Counter.builder("reservas.conflitos")
                .description("Tentativas de reserva recusadas por conflito de horário (409)").register(registro);
    }

    void criada() {
        criadas.increment();
    }

    void conflito() {
        conflitos.increment();
    }
}
