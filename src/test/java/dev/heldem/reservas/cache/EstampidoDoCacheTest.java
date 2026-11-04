package dev.heldem.reservas.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;

/**
 * Estouro de cache (cache stampede): o cache esvazia (a chave expira, ou alguém o limpa) e N requisições chegam juntas.
 * Todas dão "miss" ao mesmo tempo e TODAS vão ao banco fazer a mesma consulta, justo quando o banco mais precisa de
 * descanso. Conta, a cada rodada, as consultas que de fato chegaram ao banco (estatísticas do Hibernate) e os misses
 * registrados pelo Micrometer, com 50 requisições idênticas disparadas juntas sobre o cache vazio.
 *
 * <p>Com {@code sync = true} e o writer com lock (CacheConfig): uma só consulta por rodada. Os números de antes (sem
 * sync, e com sync mas sem lock) estão no README.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EstampidoDoCacheTest extends ApiTestBase {

    private static final int REQUISICOES = 50;
    private static final int RODADAS = 10;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    EntityManagerFactory fabrica;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    @Test
    void cincoRequisicoesJuntasSobreOCacheVazioFazemUmaSoConsultaNoBanco() throws Exception {
        for (int i = 0; i < 200; i++) {
            jdbc.update("insert into salas (id, nome, capacidade, criada_em) values (gen_random_uuid(), ?, 10, now())",
                    "Sala " + i);
        }
        var estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);

        rodada(); // aquecimento: classes, pool de conexões, JIT

        List<Long> consultas = new ArrayList<>();
        List<Double> misses = new ArrayList<>();
        for (int r = 0; r < RODADAS; r++) {
            limparCache();
            estatisticas.clear();
            double antes = gets("miss");
            rodada();
            consultas.add(estatisticas.getPrepareStatementCount());
            misses.add(gets("miss") - antes);
        }

        System.out.printf("ESTAMPIDO %d requisicoes por rodada, %d rodadas: consultas ao banco por rodada = %s; "
                + "misses por rodada = %s%n", REQUISICOES, RODADAS, consultas, misses);
        // as 50 respostas são sempre corretas; o que muda é quantas vezes o banco foi consultado
        assertThat(consultas).allMatch(c -> c <= 1);
    }

    private double gets(String resultado) {
        var contador = metricas.find("cache.gets").tag("cache", "salas").tag("result", resultado).functionCounter();
        return contador == null ? 0 : contador.count();
    }

    private void limparCache() {
        try (var conexao = redisConnection()) {
            conexao.serverCommands().flushAll();
        }
    }

    @Autowired
    org.springframework.data.redis.connection.RedisConnectionFactory redisFactory;

    private org.springframework.data.redis.connection.RedisConnection redisConnection() {
        return redisFactory.getConnection();
    }

    private void rodada() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REQUISICOES);
        try {
            List<Future<HttpStatus>> futuros = new ArrayList<>();
            for (int i = 0; i < REQUISICOES; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return HttpStatus.valueOf(get("/salas").exchange().getResponse().getStatus());
                }));
            }
            largada.countDown();
            for (Future<HttpStatus> f : futuros) {
                assertThat(f.get()).isEqualTo(HttpStatus.OK);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
