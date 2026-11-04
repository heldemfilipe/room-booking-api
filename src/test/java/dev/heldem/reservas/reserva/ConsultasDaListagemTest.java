package dev.heldem.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.heldem.reservas.ApiTestBase;
import dev.heldem.reservas.sala.Sala;
import dev.heldem.reservas.sala.SalaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * O problema N+1 da listagem: GET /reservas devolve o nome da sala junto de cada reserva. Com a sala LAZY, cada reserva
 * de uma sala ainda não carregada dispara uma consulta própria. Aqui as consultas são contadas pelas estatísticas do
 * Hibernate (statements preparados), com o contexto de persistência limpo antes da chamada.
 */
class ConsultasDaListagemTest extends ApiTestBase {

    @Autowired
    SalaRepository salaRepository;

    @Autowired
    ReservaRepository reservaRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Statistics estatisticas;

    @BeforeEach
    void ligarEstatisticas() {
        estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
    }

    /** Cria {@code reservas} reservas distribuídas em {@code salas} salas, e limpa o cache da sessão. */
    private void popular(int salas, int reservas) {
        var salasCriadas = new java.util.ArrayList<Sala>();
        for (int i = 0; i < salas; i++) {
            salasCriadas.add(salaRepository.save(new Sala("Sala " + i, 10, Instant.now())));
        }
        for (int i = 0; i < reservas; i++) {
            Instant inicio = BASE.plusSeconds(i * 3600L);
            reservaRepository.save(new Reserva(salasCriadas.get(i % salas), "ana", inicio, inicio.plusSeconds(1800),
                    Instant.now()));
        }
        entityManager.flush();
        entityManager.clear(); // sem isso a sessão já teria as salas e o N+1 não apareceria
    }

    private long consultasParaListar(int tamanho) {
        estatisticas.clear();
        // exchange() uma vez só: o builder do MockMvcTester é preguiçoso e cada assertThat sobre ele chamaria a API de novo
        var resposta = mvc.get().uri("/reservas?tamanho={t}", tamanho).exchange();
        long consultas = estatisticas.getPrepareStatementCount();
        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.itens.length()").isEqualTo(tamanho);
        return consultas;
    }

    @Test
    void cinquentaReservasEmCinquentaSalasNaoFazemUmaConsultaPorReserva() {
        popular(50, 50);

        long consultas = consultasParaListar(50);

        // uma da página, uma do total; a sala vem junto, sem consulta extra por reserva
        assertThat(consultas).as("consultas ao listar 50 reservas de 50 salas").isLessThanOrEqualTo(2);
    }

    @Test
    void cinquentaReservasEmDezSalasTambemNaoFazemConsultaPorSala() {
        popular(10, 50);

        long consultas = consultasParaListar(50);

        assertThat(consultas).as("consultas ao listar 50 reservas de 10 salas").isLessThanOrEqualTo(2);
    }
}
