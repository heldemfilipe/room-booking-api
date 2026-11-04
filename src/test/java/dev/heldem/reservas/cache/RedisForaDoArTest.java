package dev.heldem.reservas.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;

import dev.heldem.reservas.ApiTestBase;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * O cache é uma otimização, não uma dependência: com o Redis fora do ar a listagem de salas continua respondendo, pelo
 * banco. "Fora do ar" aqui é o contêiner PAUSADO (as conexões ficam abertas e não respondem), o caso que mais prende
 * uma aplicação: não dá erro de conexão recusada, dá espera. Os timeouts de 1 s limitam essa espera.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RedisForaDoArTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    @Qualifier("redisContainer")
    GenericContainer<?> redis;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    @Test
    void listagemContinuaFuncionandoSemRedisERegistraAsFalhas() throws Exception {
        criarSala("Sala sem cache", 8);
        double antes = erros();

        var cliente = redis.getDockerClient();
        cliente.pauseContainerCmd(redis.getContainerId()).exec();
        try {
            var resposta = get("/salas").exchange();
            assertThat(resposta).hasStatus(HttpStatus.OK);
            assertThat(resposta.getResponse().getContentAsString()).contains("Sala sem cache");
        } finally {
            cliente.unpauseContainerCmd(redis.getContainerId()).exec();
        }

        assertThat(erros()).isGreaterThan(antes);
    }

    private double erros() {
        return metricas.find("reservas.cache.erros").counters().stream().mapToDouble(c -> c.count()).sum();
    }
}
