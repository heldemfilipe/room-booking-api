package dev.heldem.reservas.concorrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.ApiTestBase;

/**
 * A exclusion constraint SOZINHA, sem a aplicação: 50 conexões inserem em SQL puro a mesma sala no mesmo horário, sem
 * consulta prévia nenhuma. Se só uma entra, a garantia é do banco, e não de código da API.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RestricaoDeExclusaoTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas");
        jdbc.update("delete from salas");
    }

    private UUID novaSala() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into salas (id, nome, capacidade, criada_em) values (?, ?, 10, now())", id, "Sala " + id);
        return id;
    }

    private void inserir(UUID sala, Instant inicio, Instant fim, String status) {
        jdbc.update("insert into reservas (id, sala_id, usuario_id, inicio, fim, status, criada_em) "
                + "values (?, ?, 'x', ?, ?, ?, now())", UUID.randomUUID(), sala, Timestamp.from(inicio),
                Timestamp.from(fim), status);
    }

    @Test
    void cinquentaInsertsSimultaneosEmSqlPuroGravamSoUmaReserva() throws Exception {
        UUID sala = novaSala();
        Instant inicio = Instant.parse("2026-11-10T10:00:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(50);
        var prontos = new CountDownLatch(50);
        var largada = new CountDownLatch(1);
        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futuros.add(executor.submit(() -> {
                prontos.countDown();
                largada.await();
                try {
                    inserir(sala, inicio, inicio.plusSeconds(3600), "ATIVA");
                    return true;
                } catch (DataAccessException ex) {
                    // perder tem duas formas: a constraint recusa (23P01) ou o PostgreSQL derruba a transação por
                    // deadlock (40P01), que acontece quando várias disputam o mesmo intervalo ao mesmo tempo
                    assertThat(ex.getMessage()).containsAnyOf("ex_reservas_sem_sobreposicao", "deadlock detected");
                    return false;
                }
            }));
        }
        prontos.await();
        largada.countDown();
        int gravadas = 0;
        for (Future<Boolean> futuro : futuros) {
            if (futuro.get()) {
                gravadas++;
            }
        }
        executor.shutdown();

        assertThat(gravadas).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reservas where sala_id = ?", Integer.class, sala))
                .isEqualTo(1);
    }

    @Test
    void aRestricaoRespeitaOsLimitesDoIntervaloEOStatus() {
        UUID sala = novaSala();
        Instant dez = Instant.parse("2026-11-10T10:00:00Z");
        inserir(sala, dez, dez.plusSeconds(3600), "ATIVA"); // 10h-11h

        // encostar (11h-12h) não é sobrepor: [) é fechado no início e aberto no fim
        inserir(sala, dez.plusSeconds(3600), dez.plusSeconds(7200), "ATIVA");
        // uma reserva CANCELADA não ocupa o horário, nem bloqueia as outras
        inserir(sala, dez, dez.plusSeconds(3600), "CANCELADA");
        // outra sala no mesmo horário
        inserir(novaSala(), dez, dez.plusSeconds(3600), "ATIVA");

        // sobrepor parcialmente, conter ou ser contida: recusado
        for (long[] segundos : new long[][] { { 1800, 5400 }, { -1800, 1800 }, { 900, 2700 }, { -600, 4200 } }) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> inserir(sala, dez.plusSeconds(segundos[0]), dez.plusSeconds(segundos[1]), "ATIVA"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ex_reservas_sem_sobreposicao");
        }
    }
}
