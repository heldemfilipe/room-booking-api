package dev.heldem.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

/** Os logs saem em JSON, uma linha por evento, com o traceId da requisição que os gerou (Etapa 5). */
@ExtendWith(OutputCaptureExtension.class)
class LogsEstruturadosTest extends ApiTestBase {

    @Test
    void logDeUmaRequisicaoEmJsonComTraceId(CapturedOutput saida) {
        String sala = criarSala("Sala dos logs", 5);
        assertThat(reservar(sala, "ana", 0, 2)).hasStatus(HttpStatus.CREATED);
        assertThat(reservar(sala, "bia", 1, 3)).hasStatus(HttpStatus.CONFLICT);

        String linha = saida.getAll().lines().filter(l -> l.contains("Conflito (409)")).findFirst().orElseThrow();
        assertThat(linha).startsWith("{").endsWith("}")
                .containsPattern("\"traceId\":\"[0-9a-f]{32}\"")
                .containsPattern("\"spanId\":\"[0-9a-f]{16}\"")
                .contains("\"level\":\"INFO\"");
    }
}
