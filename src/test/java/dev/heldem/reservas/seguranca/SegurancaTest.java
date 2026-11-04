package dev.heldem.reservas.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import dev.heldem.reservas.ApiTestBase;
import dev.heldem.reservas.TokensDeTeste;

/**
 * Autenticação e autorização de ponta a ponta: tokens JWT de verdade (assinados com uma chave de teste) passam pela
 * cadeia de filtros, pelo conversor de papéis do Keycloak e pela regra de dono.
 */
class SegurancaTest extends ApiTestBase {

    private static final String SALA_JSON = "{\"nome\":\"Sala Nova\",\"capacidade\":6}";

    // ---------- 401: sem token ou com token inválido ----------

    @Test
    void semTokenDevolve401ComProblemDetailEWwwAuthenticateEmTodasAsRotas() {
        var resposta = mvc.get().uri("/salas").exchange();

        assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(resposta.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Não autenticado");
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo(401);

        assertThat(mvc.get().uri("/salas/{id}", UUID.randomUUID())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/reservas")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/reservas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/salas").contentType(MediaType.APPLICATION_JSON).content(SALA_JSON))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri("/reservas/{id}", UUID.randomUUID())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenExpiradoDeOutroEmissorOuAssinadoPorOutraChaveDevolve401() {
        assertThat(getComToken(TokensDeTeste.tokenExpirado("ana", "USER"), "/salas")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(getComToken(TokensDeTeste.tokenDeOutroEmissor("ana", "USER"), "/salas"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(getComToken(TokensDeTeste.tokenAssinadoPorOutraChave("ana", "ADMIN"), "/salas"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lixoNoCabecalhoOuEsquemaErradoDevolve401() {
        assertThat(getComToken("isto.nao.eum.jwt", "/salas")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/salas").header(HttpHeaders.AUTHORIZATION, "Basic YW5hOmFuYQ=="))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    // ---------- 403: papel errado ----------

    @Test
    void usuarioComumLeSalasMasNaoEscreveDevolve403() {
        String sala = criarSala("Sala Azul", 8);
        String token = TokensDeTeste.token("ana", "USER");

        assertThat(getComToken(token, "/salas")).hasStatusOk();
        assertThat(getComToken(token, "/salas/{id}", sala)).hasStatusOk();

        var criar = postComToken(token, "/salas", SALA_JSON);
        assertThat(criar).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(criar).bodyJson().extractingPath("$.title").isEqualTo("Acesso negado");
        assertThat(mvc.put().uri("/salas/{id}", sala).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(SALA_JSON)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(deleteComToken(token, "/salas/{id}", sala)).hasStatus(HttpStatus.FORBIDDEN);

        // nada mudou: a sala continua lá e a nova não foi criada
        assertThat(get("/salas/{id}", sala)).hasStatusOk();
        assertThat(get("/salas")).bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void adminEscreveEmSalas() {
        var resposta = postComToken(TokensDeTeste.token("admin", "ADMIN"), "/salas", SALA_JSON);

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void oPapelVemDeRealmAccessRolesENaoDistingueMaiusculasNemAceitaOutrosClaims() {
        // o Keycloak manda outros papéis do realm junto (offline_access, uma_authorization); ADMIN entre eles basta
        assertThat(postComToken(TokensDeTeste.token("a", "offline_access", "ADMIN", "uma_authorization"), "/salas",
                SALA_JSON)).hasStatus(HttpStatus.CREATED);
        // papel em minúsculas não é ADMIN: hasRole("ADMIN") compara exatamente
        assertThat(postComToken(TokensDeTeste.token("a", "admin"), "/salas", "{\"nome\":\"Outra\",\"capacidade\":6}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenSemPapelNenhumAutenticaMasNaoEscreve() {
        String token = TokensDeTeste.tokenSemPapeis("sem-papel");

        assertThat(getComToken(token, "/salas")).hasStatusOk();
        assertThat(postComToken(token, "/salas", SALA_JSON)).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---------- regra de dono ----------

    @Test
    void soQuemCriouAReservaCancela() {
        String sala = criarSala("Sala Azul", 8);
        String reserva = criarReserva(sala, "ana", 0, 1);

        // bia (USER) tenta cancelar a reserva da ana: 403, e a reserva continua ATIVA
        var alheia = cancelar(reserva, "bia");
        assertThat(alheia).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(alheia).bodyJson().extractingPath("$.detail").isEqualTo("Só quem criou a reserva pode cancelá-la.");
        assertThat(get("/reservas/{id}", reserva)).bodyJson().extractingPath("$.status").isEqualTo("ATIVA");

        // nem o ADMIN cancela a reserva de outra pessoa (decisão: dono é dono)
        assertThat(deleteComToken(TokensDeTeste.token("admin", "ADMIN"), "/reservas/{id}", reserva))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/reservas/{id}", reserva)).bodyJson().extractingPath("$.status").isEqualTo("ATIVA");

        // a ana cancela a própria: 204
        assertThat(cancelar(reserva, "ana")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get("/reservas/{id}", reserva)).bodyJson().extractingPath("$.status").isEqualTo("CANCELADA");
    }

    @Test
    void oDonoDaReservaVemDoTokenENuncaDoCorpo() {
        String sala = criarSala("Sala Azul", 8);

        // o corpo tenta reservar em nome da bia; quem reserva é a ana, e é o sub dela que fica gravado
        var resposta = postComToken(TokensDeTeste.token("ana", "USER"), "/reservas", "{\"salaId\":\"" + sala
                + "\",\"usuarioId\":\"bia\",\"inicio\":\"" + BASE + "\",\"fim\":\"" + BASE.plusSeconds(3600) + "\"}");

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta).bodyJson().extractingPath("$.usuarioId").isEqualTo("ana");
    }

    @Test
    void cancelarReservaInexistenteContinuaSendo404ParaOUsuarioAutenticado() {
        assertThat(cancelar(UUID.randomUUID().toString(), "ana")).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------- o que não foi liberado de propósito não é servido ----------

    @Test
    void saudePublicaEOrestoNegadoPorPadrao() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();

        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
        // autenticado, mas a rota não foi liberada: negado, e não 404
        assertThat(get("/rota-que-nao-existe")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/actuator/env")).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---------- CORS explícito ----------

    @Test
    void corsLiberaSoAsOrigensConfiguradas() {
        var permitida = mvc.options().uri("/reservas")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange();
        assertThat(permitida).hasStatusOk();
        assertThat(permitida.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");

        var negada = mvc.options().uri("/reservas")
                .header(HttpHeaders.ORIGIN, "http://site-malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange();
        assertThat(negada).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(negada.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
