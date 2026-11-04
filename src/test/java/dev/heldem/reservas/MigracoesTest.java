package dev.heldem.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// As migrations rodam num PostgreSQL vazio e o banco recusa o que as regras de schema proíbem, mesmo sem a aplicação.
// Sem @Transactional: uma violação aborta a transação do PostgreSQL, então cada comando roda no seu (autocommit).
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigracoesTest {

    @Autowired
    JdbcTemplate jdbc;

    private final UUID salaId = UUID.randomUUID();

    private void inserirSala(UUID id, String nome, int capacidade) {
        jdbc.update("insert into salas (id, nome, capacidade, criada_em) values (?, ?, ?, now())", id, nome,
                capacidade);
    }

    private void inserirReserva(UUID sala, Instant inicio, Instant fim, String status) {
        jdbc.update("insert into reservas (id, sala_id, usuario_id, inicio, fim, status, criada_em) "
                + "values (?, ?, 'ana', ?, ?, ?, now())", UUID.randomUUID(), sala, Timestamp.from(inicio),
                Timestamp.from(fim), status);
    }

    @AfterEach
    void limpar() {
        jdbc.update("delete from reservas where sala_id = ?", salaId);
        jdbc.update("delete from salas where id = ?", salaId);
    }

    @Test
    void asMigracoesRodaramEmBancoVazioNaOrdemEComSucesso() {
        var versoes = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versoes).containsExactly("1", "2", "3");
    }

    @Test
    void checkDoBancoRecusaReservaComFimAntesOuIgualAoInicio() {
        inserirSala(salaId, "Sala CHECK " + salaId, 10);
        Instant inicio = Instant.parse("2026-11-10T10:00:00Z");

        assertThatThrownBy(() -> inserirReserva(salaId, inicio, inicio, "ATIVA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservas_intervalo");
        assertThatThrownBy(() -> inserirReserva(salaId, inicio, inicio.minusSeconds(60), "ATIVA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservas_intervalo");
        // o contrário passa: o CHECK é só sobre o intervalo
        inserirReserva(salaId, inicio, inicio.plusSeconds(3600), "ATIVA");
    }

    @Test
    void chaveEstrangeiraRecusaReservaDeSalaInexistente() {
        Instant inicio = Instant.parse("2026-11-10T10:00:00Z");

        assertThatThrownBy(() -> inserirReserva(UUID.randomUUID(), inicio, inicio.plusSeconds(3600), "ATIVA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reservas_sala_id_fkey");
    }

    @Test
    void checkDoBancoRecusaStatusInvalido() {
        inserirSala(salaId, "Sala status " + salaId, 10);
        Instant inicio = Instant.parse("2026-11-10T10:00:00Z");

        assertThatThrownBy(() -> inserirReserva(salaId, inicio, inicio.plusSeconds(3600), "TALVEZ"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservas_status");
    }

    @Test
    void salaRecusaCapacidadeZeroENomeRepetidoSemDiferenciarMaiusculas() {
        assertThatThrownBy(() -> inserirSala(salaId, "Sala zero " + salaId, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_salas_capacidade");

        inserirSala(salaId, "Sala Unica " + salaId, 10);
        assertThatThrownBy(() -> inserirSala(UUID.randomUUID(), ("sala unica " + salaId).toUpperCase(), 5))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_salas_nome");
    }
}
