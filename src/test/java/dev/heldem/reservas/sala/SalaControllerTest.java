package dev.heldem.reservas.sala;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.heldem.reservas.ApiTestBase;

class SalaControllerTest extends ApiTestBase {

    @Test
    void postValidoDevolve201ComLocationEASalaCriada() {
        var resposta = post("/salas", "{\"nome\":\"  Sala Azul \",\"capacidade\":8}");

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta).bodyJson().extractingPath("$.nome").isEqualTo("Sala Azul");
        assertThat(resposta).bodyJson().extractingPath("$.capacidade").isEqualTo(8);
        assertThat(resposta).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(get(resposta.getResponse().getHeader("Location")))
                .hasStatusOk().bodyJson().extractingPath("$.nome").isEqualTo("Sala Azul");
    }

    @Test
    void semNomeOuComCapacidadeForaDoLimiteDevolve400ComOCampo() {
        var semNome = post("/salas", "{\"capacidade\":8}");
        assertThat(semNome).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(semNome).bodyJson().extractingPath("$.erros.nome").isNotNull();

        assertThat(post("/salas", "{\"nome\":\"X\",\"capacidade\":0}")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.erros.capacidade").isNotNull();
        assertThat(post("/salas", "{\"nome\":\"X\",\"capacidade\":1001}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/salas", "{\"nome\":\"" + "x".repeat(101) + "\",\"capacidade\":5}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nomeRepetidoSemDiferenciarMaiusculasDevolve409() {
        criarSala("Sala Verde", 10);

        var repetida = post("/salas", "{\"nome\":\"  SALA verde \",\"capacidade\":5}");

        assertThat(repetida).hasStatus(HttpStatus.CONFLICT);
        assertThat(repetida).bodyJson().extractingPath("$.title").isEqualTo("Conflito");
    }

    @Test
    void listarDevolveAsSalasEmOrdemAlfabetica() {
        criarSala("Zeta", 4);
        criarSala("Alfa", 6);
        criarSala("Meio", 8);

        var lista = get("/salas");

        assertThat(lista).hasStatusOk();
        assertThat(lista).bodyJson().extractingPath("$[0].nome").isEqualTo("Alfa");
        assertThat(lista).bodyJson().extractingPath("$[1].nome").isEqualTo("Meio");
        assertThat(lista).bodyJson().extractingPath("$[2].nome").isEqualTo("Zeta");
    }

    @Test
    void atualizarMudaNomeECapacidadeEAceitaManterOProprioNome() {
        String id = criarSala("Sala A", 10);

        var mesmoNome = put("/salas/" + id, "{\"nome\":\"Sala A\",\"capacidade\":12}");
        assertThat(mesmoNome).hasStatusOk();
        assertThat(mesmoNome).bodyJson().extractingPath("$.capacidade").isEqualTo(12);

        var novoNome = put("/salas/" + id, "{\"nome\":\"Sala A2\",\"capacidade\":12}");
        assertThat(novoNome).hasStatusOk();
        assertThat(novoNome).bodyJson().extractingPath("$.nome").isEqualTo("Sala A2");
    }

    @Test
    void atualizarParaONomeDeOutraSalaDevolve409() {
        criarSala("Sala B", 10);
        String outra = criarSala("Sala C", 10);

        assertThat(put("/salas/" + outra, "{\"nome\":\"sala b\",\"capacidade\":10}")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void salaInexistenteDevolve404EIdQueNaoEUuidDevolve400() {
        assertThat(get("/salas/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(put("/salas/" + UUID.randomUUID(), "{\"nome\":\"X\",\"capacidade\":5}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(delete("/salas/{id}", UUID.randomUUID()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get("/salas/{id}", "abc")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removerSalaSemReservasDevolve204EDepoisDeRemovidaDevolve404() {
        String id = criarSala("Sala D", 10);

        assertThat(delete("/salas/{id}", id).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get("/salas/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void removerSalaComReservasDevolve409ENaoApagaNada() {
        String id = criarSala("Sala E", 10);
        criarReserva(id, "ana", 0, 1);

        var resposta = delete("/salas/{id}", id).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
        assertThat(resposta).bodyJson().extractingPath("$.detail").asString().contains("tem reservas");
        assertThat(get("/salas/{id}", id)).hasStatusOk();
    }
}
