package dev.heldem.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import io.micrometer.core.instrument.MeterRegistry;

/** Sondas, métricas (técnicas e de negócio) e o endpoint de diagnóstico desligado por padrão (Etapa 5). */
class ObservabilidadeTest extends ApiTestBase {

    @Autowired
    MeterRegistry metricas;

    private double contador(String nome) {
        var c = metricas.find(nome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void livenessEReadinessSaoPublicasESeparadas() {
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange()).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        var readiness = mvc.get().uri("/actuator/health/readiness").exchange();
        assertThat(readiness).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusExpoeMetricasTecnicasEDeNegocioSemToken() throws Exception {
        String sala = criarSala("Sala das metricas", 5);
        assertThat(reservar(sala, "ana", 0, 1)).hasStatus(HttpStatus.CREATED);

        var resposta = mvc.get().uri("/actuator/prometheus").exchange();
        assertThat(resposta).hasStatus(HttpStatus.OK);
        String texto = resposta.getResponse().getContentAsString();
        assertThat(texto).contains("http_server_requests_seconds_bucket") // histograma para o p95
                .contains("reservas_criadas_total").contains("reservas_conflitos_total");
    }

    @Test
    void reservaCriadaEConflitoSaoContados() {
        double criadas = contador("reservas.criadas");
        double conflitos = contador("reservas.conflitos");
        String sala = criarSala("Sala contada", 5);

        assertThat(reservar(sala, "ana", 0, 2)).hasStatus(HttpStatus.CREATED);
        assertThat(reservar(sala, "bia", 1, 3)).hasStatus(HttpStatus.CONFLICT);

        assertThat(contador("reservas.criadas") - criadas).isEqualTo(1);
        assertThat(contador("reservas.conflitos") - conflitos).isEqualTo(1);
    }

    @Test
    void replayDeIdempotencyKeyNaoContaComoReservaCriada() {
        String sala = criarSala("Sala do replay", 5);
        assertThat(reservarComChave(sala, "ana", "k-1", 0, 1)).hasStatus(HttpStatus.CREATED);
        double depoisDaPrimeira = contador("reservas.criadas");

        var replay = reservarComChave(sala, "ana", "k-1", 0, 1);

        assertThat(replay).hasStatus(HttpStatus.CREATED);
        assertThat(replay.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(contador("reservas.criadas")).isEqualTo(depoisDaPrimeira);
    }

    @Test
    void endpointDeLentidaoNaoExistePorPadrao() {
        // desligado (app.diagnostico.habilitado=false): nem o ADMIN o alcança
        assertThat(get("/diagnostico/lento?segundos=0").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
