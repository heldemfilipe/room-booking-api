package dev.heldem.reservas.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;

/**
 * O experimento da inconsistência: altero a sala DIRETO NO BANCO (como um script, um DBA ou outro serviço faria, sem passar
 * pelo @CacheEvict) e a API segue devolvendo o valor antigo até o TTL vencer. Para o teste ser rápido, em vez de esperar os
 * 5 minutos de verdade (app.cache.salas-ttl, conferidos em CacheDeSalasTest), encurto para 2 s o TTL da entrada no Redis.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TtlDoCacheTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    private String listar() throws Exception {
        var resposta = get("/salas").exchange();
        assertThat(resposta).hasStatus(HttpStatus.OK);
        return resposta.getResponse().getContentAsString();
    }

    @Test
    void valorAntigoPersisteAteOTtlEDepoisOBancoVoltaAAparecer() throws Exception {
        criarSala("Capacidade antiga", 8);
        assertThat(listar()).contains("\"capacidade\":8");

        jdbc.update("update salas set capacidade = 99");
        String chave = redis.keys("reservas:salas::*").iterator().next();
        redis.expire(chave, 2, TimeUnit.SECONDS);

        assertThat(listar()).contains("\"capacidade\":8").doesNotContain("\"capacidade\":99"); // velho, do cache

        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(listar()).contains("\"capacidade\":99")); // venceu o TTL: releu do banco
    }
}
