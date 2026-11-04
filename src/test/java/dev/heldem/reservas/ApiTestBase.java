package dev.heldem.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base dos testes de ponta a ponta: sobe a aplicação contra um PostgreSQL 17 de verdade (Testcontainers, sem mock de
 * banco), chama a API por MockMvc com tokens JWT de verdade e desfaz o que gravou. Todas as classes que a estendem
 * compartilham um só contêiner.
 *
 * <p>Identidades: {@code ADMIN} (papel ADMIN) é o padrão dos helpers de salas e de leitura; {@code reservar} e
 * {@code cancelar} agem como um usuário comum (papel USER) cujo {@code sub} é o nome passado ("ana", "bia").
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TokensDeTeste.class })
@Transactional
public abstract class ApiTestBase {

    /** Um dia fixo no futuro; os testes montam os horários a partir dele. */
    protected static final Instant BASE = Instant.parse("2026-11-10T10:00:00Z");

    protected static final String ADMIN = "admin";

    @Autowired
    protected MockMvcTester mvc;

    // 50 conexões, e não as 10 padrão do Hikari: os testes de concorrência disparam 50 requisições ao mesmo tempo e
    // querem 50 sessões brigando no banco de verdade (com 10, as outras esperariam por uma conexão do pool). Vale para
    // todas as classes que estendem esta base, então elas continuam compartilhando um só contexto do Spring.
    @DynamicPropertySource
    static void tamanhoDoPool(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String idDoLocation(MvcTestResult resposta) {
        String local = resposta.getResponse().getHeader("Location");
        return local.substring(local.lastIndexOf('/') + 1);
    }

    // --- chamadas como ADMIN (padrão dos testes de salas e de leitura) ---

    protected MockMvcTester.MockMvcRequestBuilder get(String uri, Object... variaveis) {
        return getComToken(TokensDeTeste.token(ADMIN, "ADMIN"), uri, variaveis);
    }

    protected MvcTestResult post(String uri, String json) {
        return postComToken(TokensDeTeste.token(ADMIN, "ADMIN"), uri, json);
    }

    protected MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(TokensDeTeste.token(ADMIN, "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MockMvcTester.MockMvcRequestBuilder delete(String uri, Object... variaveis) {
        return mvc.delete().uri(uri, variaveis)
                .header(HttpHeaders.AUTHORIZATION, bearer(TokensDeTeste.token(ADMIN, "ADMIN")));
    }

    // --- chamadas com um token específico ---

    protected MockMvcTester.MockMvcRequestBuilder getComToken(String token, String uri, Object... variaveis) {
        return mvc.get().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    protected MvcTestResult postComToken(String token, String uri, String json) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    /** Reserva com Idempotency-Key, em nome do usuário informado (papel USER). */
    protected MvcTestResult reservarComChave(String salaId, String usuario, String chave, int horaInicio, int horaFim) {
        var requisicao = mvc.post().uri("/reservas")
                .header(HttpHeaders.AUTHORIZATION, bearer(TokensDeTeste.token(usuario, "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salaId\":\"" + salaId + "\",\"inicio\":\"" + BASE.plusSeconds(horaInicio * 3600L)
                        + "\",\"fim\":\"" + BASE.plusSeconds(horaFim * 3600L) + "\"}");
        return (chave == null ? requisicao : requisicao.header("Idempotency-Key", chave)).exchange();
    }

    protected MvcTestResult deleteComToken(String token, String uri, Object... variaveis) {
        return mvc.delete().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }

    // --- salas e reservas ---

    protected String criarSala(String nome, int capacidade) {
        var resposta = post("/salas", "{\"nome\":\"" + nome + "\",\"capacidade\":" + capacidade + "}");
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return idDoLocation(resposta);
    }

    /** Horário relativo à BASE: reservar(sala, "ana", 0, 1) é das 10h às 11h, em nome do usuário "ana" (papel USER). */
    protected MvcTestResult reservar(String salaId, String usuario, int horaInicio, int horaFim) {
        return postComToken(TokensDeTeste.token(usuario, "USER"), "/reservas", "{\"salaId\":\"" + salaId
                + "\",\"inicio\":\"" + BASE.plusSeconds(horaInicio * 3600L) + "\",\"fim\":\""
                + BASE.plusSeconds(horaFim * 3600L) + "\"}");
    }

    protected String criarReserva(String salaId, String usuario, int horaInicio, int horaFim) {
        var resposta = reservar(salaId, usuario, horaInicio, horaFim);
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return idDoLocation(resposta);
    }

    /** Cancela como o usuário informado (papel USER). */
    protected MvcTestResult cancelar(String reservaId, String usuario) {
        return deleteComToken(TokensDeTeste.token(usuario, "USER"), "/reservas/{id}", reservaId);
    }
}
