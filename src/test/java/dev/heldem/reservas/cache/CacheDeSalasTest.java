package dev.heldem.reservas.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;
import dev.heldem.reservas.TokensDeTeste;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Cache da listagem de salas. Sem transação de teste: o cache é transacional (só grava e apaga depois do commit), então um
 * teste que desfaz tudo no fim nunca veria o cache funcionar.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CacheDeSalasTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from chaves_idempotencia");
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    private double gets(String resultado) {
        var contador = metricas.find("cache.gets").tag("cache", "salas").tag("result", resultado).functionCounter();
        return contador == null ? 0 : contador.count();
    }

    private String listar() throws Exception {
        var resposta = get("/salas").exchange();
        assertThat(resposta).hasStatus(HttpStatus.OK);
        return resposta.getResponse().getContentAsString();
    }

    @Test
    void segundaListagemVemDoCacheEContaComoAcerto() throws Exception {
        criarSala("Sala do cache", 8);
        double acertos = gets("hit");

        String primeira = listar();
        double errosDepoisDaPrimeira = gets("miss");
        assertThat(errosDepoisDaPrimeira).isPositive(); // a primeira foi ao banco
        assertThat(gets("hit")).isEqualTo(acertos);

        String segunda = listar();

        assertThat(segunda).isEqualTo(primeira).contains("Sala do cache");
        assertThat(gets("hit") - acertos).isEqualTo(1);              // a segunda veio do cache
        assertThat(gets("miss")).isEqualTo(errosDepoisDaPrimeira);   // e não gerou miss novo
    }

    @Test
    void escritaPelaApiEsvaziaOCacheNaHora() throws Exception {
        String sala = criarSala("Sala velha", 8);
        listar();
        assertThat(listar()).contains("Sala velha"); // já está no cache

        assertThat(put("/salas/" + sala, "{\"nome\":\"Sala nova\",\"capacidade\":9}")).hasStatus(HttpStatus.OK);
        assertThat(listar()).contains("Sala nova").doesNotContain("Sala velha");

        criarSala("Outra sala", 4);
        assertThat(listar()).contains("Outra sala");

        assertThat(delete("/salas/{id}", sala).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(listar()).doesNotContain("Sala nova");
    }

    @Test
    void alteracaoDiretoNoBancoNaoApareceEnquantoOCacheNaoVence() throws Exception {
        criarSala("Nome original", 8);
        listar();

        jdbc.update("update salas set nome = 'Nome mudado no banco'");

        // o cache continua servindo o valor antigo: é o preço (aceito) do cache, limitado pelo TTL
        assertThat(listar()).contains("Nome original").doesNotContain("Nome mudado no banco");
    }

    @Test
    void entradaFicaNoRedisComTtlDeCincoMinutosEJsonLegivel() throws Exception {
        criarSala("Sala no redis", 8);
        listar();

        var chaves = redis.keys("reservas:salas::*");
        assertThat(chaves).hasSize(1);
        String chave = chaves.iterator().next();
        Long segundos = redis.getExpire(chave);
        assertThat(segundos).isBetween(280L, 300L);
        assertThat(redis.opsForValue().get(chave)).startsWith("[{").contains("\"nome\":\"Sala no redis\"")
                .doesNotContain("@class"); // JSON tipado: sem nome de classe dentro do valor
    }

    @Test
    void metricasDoCacheSoParaAdmin() {
        String uri = "/actuator/metrics/cache.gets";
        assertThat(mvc.get().uri(uri).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(getComToken(TokensDeTeste.token("ana", "USER"), uri).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(uri).exchange()).hasStatus(HttpStatus.OK);
        // o que não está exposto continua fechado, mesmo para o ADMIN
        assertThat(get("/actuator/env").exchange()).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aChaveEUmaSoParaQualquerUsuario() throws Exception {
        // a listagem não depende de quem pergunta, então não há uma entrada por usuário
        criarSala("Sala " + UUID.randomUUID(), 8);
        listar();
        assertThat(getComToken(TokensDeTeste.token("ana", "USER"), "/salas").exchange()).hasStatus(HttpStatus.OK);
        assertThat(redis.keys("reservas:salas::*")).hasSize(1);
    }
}
