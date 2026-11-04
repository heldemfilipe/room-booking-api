package dev.heldem.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base dos testes de ponta a ponta: sobe a aplicação contra um PostgreSQL 17 de verdade (Testcontainers, sem mock de
 * banco), chama a API por MockMvc e desfaz o que gravou. Todas as classes que a estendem compartilham um só contêiner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class ApiTestBase {

    /** Um dia fixo no futuro; os testes montam os horários a partir dele. */
    protected static final Instant BASE = Instant.parse("2026-11-10T10:00:00Z");

    @Autowired
    protected MockMvcTester mvc;

    protected static String idDoLocation(MvcTestResult resposta) {
        String local = resposta.getResponse().getHeader("Location");
        return local.substring(local.lastIndexOf('/') + 1);
    }

    protected MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected String criarSala(String nome, int capacidade) {
        var resposta = post("/salas", "{\"nome\":\"" + nome + "\",\"capacidade\":" + capacidade + "}");
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return idDoLocation(resposta);
    }

    /** Horário relativo à BASE: criarReserva(sala, "ana", 0, 1) é das 10h às 11h. */
    protected MvcTestResult reservar(String salaId, String usuarioId, int horaInicio, int horaFim) {
        return post("/reservas", "{\"salaId\":\"" + salaId + "\",\"usuarioId\":\"" + usuarioId + "\",\"inicio\":\""
                + BASE.plusSeconds(horaInicio * 3600L) + "\",\"fim\":\"" + BASE.plusSeconds(horaFim * 3600L) + "\"}");
    }

    protected String criarReserva(String salaId, String usuarioId, int horaInicio, int horaFim) {
        var resposta = reservar(salaId, usuarioId, horaInicio, horaFim);
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return idDoLocation(resposta);
    }
}
