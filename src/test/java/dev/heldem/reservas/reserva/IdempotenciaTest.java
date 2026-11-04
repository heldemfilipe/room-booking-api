package dev.heldem.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.heldem.reservas.ApiTestBase;

class IdempotenciaTest extends ApiTestBase {

    private static final String REPLAY = "Idempotency-Replayed";

    @Test
    void amesmaChaveDuasVezesDevolveAMesmaReservaSemCriarOutra() {
        String sala = criarSala("Sala Azul", 8);

        var primeira = reservarComChave(sala, "ana", "chave-1", 0, 1);
        var segunda = reservarComChave(sala, "ana", "chave-1", 0, 1);

        assertThat(primeira).hasStatus(HttpStatus.CREATED);
        assertThat(primeira.getResponse().getHeader(REPLAY)).isNull();
        // a segunda é um replay: mesmo status, mesma reserva, mesmo Location, marcada como repetida
        assertThat(segunda).hasStatus(HttpStatus.CREATED);
        assertThat(segunda.getResponse().getHeader(REPLAY)).isEqualTo("true");
        assertThat(idDoLocation(segunda)).isEqualTo(idDoLocation(primeira));
        assertThat(segunda).bodyJson().extractingPath("$.id").isEqualTo(idDoLocation(primeira));
        assertThat(get("/reservas?salaId={s}", sala)).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void semAChaveORepetidoLevaOConflitoDaSobreposicao() {
        String sala = criarSala("Sala Azul", 8);

        assertThat(reservarComChave(sala, "ana", null, 0, 1)).hasStatus(HttpStatus.CREATED);
        assertThat(reservarComChave(sala, "ana", null, 0, 1)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void aMesmaChaveComOutroCorpoDevolve422() {
        String sala = criarSala("Sala Azul", 8);
        assertThat(reservarComChave(sala, "ana", "chave-1", 0, 1)).hasStatus(HttpStatus.CREATED);

        var outroHorario = reservarComChave(sala, "ana", "chave-1", 2, 3);

        assertThat(outroHorario).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(outroHorario).bodyJson().extractingPath("$.title").isEqualTo("Idempotency-Key reutilizada");
        // e nada foi criado a mais
        assertThat(get("/reservas?salaId={s}", sala)).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void aChaveVaiPorUsuarioDuasPessoasPodemUsarOMesmoTexto() {
        String sala = criarSala("Sala Azul", 8);

        var ana = reservarComChave(sala, "ana", "mesma-chave", 0, 1);
        var bia = reservarComChave(sala, "bia", "mesma-chave", 2, 3);

        assertThat(ana).hasStatus(HttpStatus.CREATED);
        assertThat(bia).hasStatus(HttpStatus.CREATED);
        assertThat(bia.getResponse().getHeader(REPLAY)).isNull();
        assertThat(idDoLocation(bia)).isNotEqualTo(idDoLocation(ana));
    }

    @Test
    void chaveVaziaOuGrandeDemaisDevolve400() {
        String sala = criarSala("Sala Azul", 8);

        assertThat(reservarComChave(sala, "ana", "   ", 0, 1)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(reservarComChave(sala, "ana", "x".repeat(101), 0, 1)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(reservarComChave(sala, "ana", "x".repeat(100), 0, 1)).hasStatus(HttpStatus.CREATED);
    }
}
