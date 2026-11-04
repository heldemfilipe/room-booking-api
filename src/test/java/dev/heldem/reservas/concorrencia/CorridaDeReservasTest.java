package dev.heldem.reservas.concorrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;

/**
 * A corrida da reserva dupla: {@value #CLIENTES} clientes, cada um com o seu usuário e a sua requisição, largam ao mesmo
 * tempo (um portão {@link CountDownLatch}) para reservar a MESMA sala no MESMO horário. A verdade do resultado é o que
 * ficou gravado no banco ({@code ativasNoBanco}), e não só os códigos HTTP.
 *
 * <p>Sem {@code @Transactional}: o que cada requisição grava precisa ser confirmado de verdade, cada uma na sua
 * transação e na sua conexão, senão não há corrida. Por isso a limpeza é manual.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CorridaDeReservasTest extends ApiTestBase {

    static final int CLIENTES = 50;
    static final int RODADAS = 10;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    record Resposta(int status, long nanos) {
    }

    /** O resultado de uma rodada de {@value #CLIENTES} requisições simultâneas. */
    record Rodada(int criadas, int conflitos, int outros, int ativasNoBanco, long totalMs, List<Long> latenciasMs) {
    }

    /** Uma rodada: sala nova, {@value #CLIENTES} clientes disputando 10h-11h. */
    Rodada rodada() throws Exception {
        String sala = criarSala("Sala da corrida " + UUID.randomUUID(), 10);
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTES);
        var prontos = new CountDownLatch(CLIENTES);
        var largada = new CountDownLatch(1);
        List<Future<Resposta>> futuros = new ArrayList<>();
        for (int i = 0; i < CLIENTES; i++) {
            String cliente = "cliente-" + i;
            futuros.add(executor.submit(() -> {
                prontos.countDown();
                largada.await(); // todos esperam aqui e saem juntos
                long inicio = System.nanoTime();
                var resposta = reservar(sala, cliente, 0, 1);
                return new Resposta(resposta.getResponse().getStatus(), System.nanoTime() - inicio);
            }));
        }
        prontos.await();
        long t0 = System.nanoTime();
        largada.countDown();

        int criadas = 0;
        int conflitos = 0;
        int outros = 0;
        List<Long> latencias = new ArrayList<>();
        for (Future<Resposta> futuro : futuros) {
            try {
                Resposta r = futuro.get();
                latencias.add(r.nanos() / 1_000_000);
                switch (r.status()) {
                    case 201 -> criadas++;
                    case 409 -> conflitos++;
                    default -> outros++;
                }
            } catch (ExecutionException ex) {
                outros++;
            }
        }
        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        executor.shutdown();

        int ativas = jdbc.queryForObject(
                "select count(*) from reservas where sala_id = ? and status = 'ATIVA'", Integer.class,
                UUID.fromString(sala));
        Collections.sort(latencias);
        return new Rodada(criadas, conflitos, outros, ativas, totalMs, latencias);
    }

    // >>> TESTE DA VERSÃO INGÊNUA (a corrida existe). Cada correção troca este teste por "somenteUmaReservaPorCorrida".
    @Test
    void reproduzACorridaNaVersaoIngenua() throws Exception {
        List<Integer> ativasPorRodada = new ArrayList<>();
        boolean duplicou = false;
        for (int r = 1; r <= RODADAS && !duplicou; r++) {
            Rodada rodada = rodada();
            ativasPorRodada.add(rodada.ativasNoBanco());
            duplicou = rodada.ativasNoBanco() > 1;
        }

        assertThat(duplicou)
                .as("a corrida deveria gravar mais de uma reserva ATIVA no mesmo horário; reservas por rodada: %s",
                        ativasPorRodada)
                .isTrue();
    }

    /**
     * Medição para a tabela de experimentos do README: só roda sob demanda
     * ({@code ./mvnw test -Dtest=CorridaDeReservasTest#medir -Dexperimento=true}) e imprime uma linha por solução.
     */
    @Test
    @EnabledIfSystemProperty(named = "experimento", matches = "true")
    void medir() throws Exception {
        List<Rodada> rodadas = new ArrayList<>();
        rodada(); // aquecimento (JIT, pool de conexões, cache de planos); não entra na conta
        limpar();
        for (int r = 0; r < RODADAS; r++) {
            rodadas.add(rodada());
        }
        var criadas = rodadas.stream().mapToInt(Rodada::criadas).summaryStatistics();
        var conflitos = rodadas.stream().mapToInt(Rodada::conflitos).summaryStatistics();
        var ativas = rodadas.stream().mapToInt(Rodada::ativasNoBanco).summaryStatistics();
        int outros = rodadas.stream().mapToInt(Rodada::outros).sum();
        var totais = rodadas.stream().mapToLong(Rodada::totalMs).sorted().boxed().toList();
        List<Long> todas = rodadas.stream().flatMap(r -> r.latenciasMs().stream()).sorted().toList();
        System.out.printf(
                "EXPERIMENTO|rodadas=%d|clientes=%d|criadas(min/max/media)=%d/%d/%.1f|ativasNoBanco(min/max/media)=%d/%d/%.1f"
                        + "|409(media)=%.1f|outros(total)=%d|rodada_ms(mediana)=%d|latencia_ms(p50/p95/max)=%d/%d/%d%n",
                RODADAS, CLIENTES, criadas.getMin(), criadas.getMax(), criadas.getAverage(), ativas.getMin(),
                ativas.getMax(), ativas.getAverage(), conflitos.getAverage(), outros,
                totais.get(totais.size() / 2), todas.get(todas.size() / 2), todas.get((int) (todas.size() * 0.95)),
                todas.get(todas.size() - 1));
    }
}
