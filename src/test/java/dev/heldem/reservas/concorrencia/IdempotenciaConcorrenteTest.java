package dev.heldem.reservas.concorrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;

/**
 * O cliente que repete: 50 requisições IDÊNTICAS (mesmo usuário, mesma Idempotency-Key, mesmo corpo) disparadas juntas,
 * como um clique duplo ou uma retentativa agressiva. Tem que sobrar uma única reserva, e todas as respostas têm que ser a
 * mesma reserva. Sem transação de teste, pelo mesmo motivo da corrida (veja CorridaDeReservasTest).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotenciaConcorrenteTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from chaves_idempotencia");
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    // Precisa de commits reais: a requisição que falha tem que desfazer a SUA transação, inclusive a linha da chave. Dentro
    // da transação compartilhada de um teste comum, a linha da chave ficaria (a transação é uma só).
    @Test
    void falhaNaoConsomeAChaveEOClientePodeTentarDeNovoComAMesma() {
        String sala = criarSala("Sala da falha " + UUID.randomUUID(), 10);
        String bloqueio = criarReserva(sala, "bia", 0, 2);

        // a primeira tentativa da ana falha por conflito (409) e NÃO memoriza a chave
        assertThat(reservarComChave(sala, "ana", "chave-1", 1, 3)).hasStatus(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from chaves_idempotencia", Integer.class)).isZero();

        assertThat(cancelar(bloqueio, "bia")).hasStatus(HttpStatus.NO_CONTENT);
        var depois = reservarComChave(sala, "ana", "chave-1", 1, 3);
        assertThat(depois).hasStatus(HttpStatus.CREATED);
        assertThat(depois.getResponse().getHeader("Idempotency-Replayed")).isNull(); // criou de verdade, não é replay
    }

    @Test
    void chaveSemReservaAssociadaDevolve409EmVezDeErroDeServidor() {
        String sala = criarSala("Sala da guarda " + UUID.randomUUID(), 10);
        // linha que o fluxo normal nunca deixa: chave registrada sem reserva (aqui, inserida à mão, com o hash certo)
        String hash = hashDe(sala, 0, 1);
        jdbc.update("insert into chaves_idempotencia (id, usuario_id, chave, hash_requisicao, criada_em) "
                + "values (?, 'ana', 'orfa', ?, now())", UUID.randomUUID(), hash);

        assertThat(reservarComChave(sala, "ana", "orfa", 0, 1)).hasStatus(HttpStatus.CONFLICT);
    }

    private static String hashDe(String sala, int horaInicio, int horaFim) {
        try {
            String conteudo = sala + "|" + BASE.plusSeconds(horaInicio * 3600L) + "|" + BASE.plusSeconds(horaFim * 3600L);
            var resumo = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(conteudo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(resumo);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void cinquentaRequisicoesComAMesmaChaveCriamUmaReservaESoDevolvemAMesma() throws Exception {
        String sala = criarSala("Sala da idempotencia " + UUID.randomUUID(), 10);
        ExecutorService executor = Executors.newFixedThreadPool(50);
        var prontos = new CountDownLatch(50);
        var largada = new CountDownLatch(1);
        List<Future<String[]>> futuros = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futuros.add(executor.submit(() -> {
                prontos.countDown();
                largada.await();
                var resposta = reservarComChave(sala, "ana", "clique-duplo", 0, 1);
                String repetida = resposta.getResponse().getHeader("Idempotency-Replayed");
                String local = resposta.getResponse().getHeader("Location");
                return new String[] { String.valueOf(resposta.getResponse().getStatus()), repetida, local };
            }));
        }
        prontos.await();
        largada.countDown();

        int criadas = 0;
        int repetidas = 0;
        int outras = 0;
        Set<String> locais = new HashSet<>();
        for (Future<String[]> futuro : futuros) {
            String[] r = futuro.get();
            if (!"201".equals(r[0])) {
                outras++;
            } else if ("true".equals(r[1])) {
                repetidas++;
                locais.add(r[2]);
            } else {
                criadas++;
                locais.add(r[2]);
            }
        }
        executor.shutdown();

        assertThat(criadas).as("respostas que criaram de verdade").isEqualTo(1);
        assertThat(repetidas).as("respostas que foram replay").isEqualTo(49);
        assertThat(outras).as("qualquer outra resposta (409, 500...)").isZero();
        assertThat(locais).as("todas apontam para a MESMA reserva").hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from reservas", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chaves_idempotencia", Integer.class)).isEqualTo(1);
    }
}
