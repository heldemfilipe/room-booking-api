package dev.heldem.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.heldem.reservas.ApiTestBase;
import dev.heldem.reservas.TokensDeTeste;

class ReservaControllerTest extends ApiTestBase {

    private static String instante(int hora) {
        return BASE.plusSeconds(hora * 3600L).toString();
    }

    @Test
    void postValidoDevolve201AtivaComLocationENomeDaSala() {
        String salaId = criarSala("Sala Azul", 8);

        var resposta = reservar(salaId, "ana", 0, 1);

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("ATIVA");
        assertThat(resposta).bodyJson().extractingPath("$.salaId").isEqualTo(salaId);
        assertThat(resposta).bodyJson().extractingPath("$.salaNome").isEqualTo("Sala Azul");
        assertThat(resposta).bodyJson().extractingPath("$.usuarioId").isEqualTo("ana");
        assertThat(resposta).bodyJson().extractingPath("$.inicio").isEqualTo(instante(0));
        assertThat(get(resposta.getResponse().getHeader("Location"))).hasStatusOk();
    }

    @Test
    void salaInexistenteDevolve404() {
        UUID inexistente = UUID.randomUUID();

        var resposta = reservar(inexistente.toString(), "ana", 0, 1);

        assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resposta).bodyJson().extractingPath("$.detail").isEqualTo("Sala " + inexistente + " não encontrada.");
    }

    @Test
    void fimAntesOuIgualAoInicioDevolve400ComOCampo() {
        String salaId = criarSala("Sala Azul", 8);

        var igual = reservar(salaId, "ana", 1, 1);
        assertThat(igual).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(igual).bodyJson().extractingPath("$.erros.intervaloValido").isEqualTo("fim deve ser depois de inicio");

        assertThat(reservar(salaId, "ana", 2, 1)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void camposAusentesEDatasForaDoFormatoDevolve400() {
        String salaId = criarSala("Sala Azul", 8);

        var vazio = postComToken(TokensDeTeste.token("ana", "USER"), "/reservas", "{}");
        assertThat(vazio).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(vazio).bodyJson().extractingPath("$.erros.salaId").isNotNull();
        assertThat(vazio).bodyJson().extractingPath("$.erros.inicio").isNotNull();
        assertThat(vazio).bodyJson().extractingPath("$.erros.fim").isNotNull();

        assertThat(postComToken(TokensDeTeste.token("ana", "USER"), "/reservas", "{\"salaId\":\"" + salaId
                + "\",\"inicio\":\"amanhã\",\"fim\":\"" + instante(1) + "\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postComToken(TokensDeTeste.token("ana", "USER"), "/reservas", "{\"salaId\":\"nao-e-uuid\",\"inicio\":\""
                + instante(0) + "\",\"fim\":\"" + instante(1) + "\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sobreposicaoNaMesmaSalaDevolve409EmQualquerFormaDeSobrepor() {
        String salaId = criarSala("Sala Azul", 8);
        criarReserva(salaId, "ana", 2, 4); // 12h-14h

        // parcial no começo, parcial no fim, contida, contendo e idêntica
        for (int[] horas : new int[][] { { 1, 3 }, { 3, 5 }, { 2, 3 }, { 1, 5 }, { 2, 4 } }) {
            var resposta = reservar(salaId, "bia", horas[0], horas[1]);
            assertThat(resposta).as("reserva %dh-%dh", horas[0], horas[1]).hasStatus(HttpStatus.CONFLICT);
            assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Conflito");
        }
        assertThat(get("/reservas?salaId={s}", salaId)).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void intervalosQueSoSeTocamNaoConflitamENemOutraSala() {
        String sala = criarSala("Sala Azul", 8);
        String outra = criarSala("Sala Verde", 8);
        criarReserva(sala, "ana", 1, 2); // 11h-12h

        assertThat(reservar(sala, "bia", 0, 1)).hasStatus(HttpStatus.CREATED);  // termina quando a outra começa
        assertThat(reservar(sala, "bia", 2, 3)).hasStatus(HttpStatus.CREATED);  // começa quando a outra termina
        assertThat(reservar(outra, "bia", 1, 2)).hasStatus(HttpStatus.CREATED); // mesmo horário, outra sala
    }

    @Test
    void reservaCanceladaLiberaOHorario() {
        String sala = criarSala("Sala Azul", 8);
        String ana = criarReserva(sala, "ana", 0, 2);
        assertThat(reservar(sala, "bia", 1, 3)).hasStatus(HttpStatus.CONFLICT);

        assertThat(cancelar(ana, "ana")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(reservar(sala, "bia", 1, 3)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void listarFiltraPorSalaEPorPeriodoEIntervalosQueSoSeTocamNaoSeSobrepoem() {
        String sala = criarSala("Sala Azul", 8);
        String outra = criarSala("Sala Verde", 8);
        criarReserva(sala, "ana", 0, 1);   // 10h-11h
        criarReserva(sala, "ana", 1, 2);   // 11h-12h
        criarReserva(sala, "ana", 2, 3);   // 12h-13h
        criarReserva(outra, "ana", 1, 2);  // outra sala, mesmo horário

        // período 11h-12h na sala azul: só a das 11h; as vizinhas apenas encostam (fim == início)
        var periodo = get("/reservas?salaId={s}&de={de}&ate={ate}", sala, instante(1), instante(2));
        assertThat(periodo).hasStatusOk();
        assertThat(periodo).bodyJson().extractingPath("$.total").isEqualTo(1);
        assertThat(periodo).bodyJson().extractingPath("$.itens[0].inicio").isEqualTo(instante(1));

        // só "de": termina depois das 11h30 -> as das 11h e 12h
        assertThat(get("/reservas?salaId={s}&de={de}", sala, BASE.plusSeconds(5400)))
                .hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(2);
        // só "ate": começa antes das 11h -> só a das 10h
        assertThat(get("/reservas?salaId={s}&ate={ate}", sala, instante(1)))
                .hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(1);
        // só a sala; sem sala, todas
        assertThat(get("/reservas?salaId={s}", sala))
                .hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(3);
        assertThat(get("/reservas"))
                .hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(4);
    }

    @Test
    void listarPaginaEmOrdemDeInicioEDevolveOsTotais() {
        String sala = criarSala("Sala Azul", 8);
        for (int hora = 5; hora >= 0; hora--) { // criadas fora de ordem de propósito
            criarReserva(sala, "ana", hora, hora + 1);
        }

        var segunda = get("/reservas?salaId={s}&tamanho=2&pagina=1", sala);

        assertThat(segunda).hasStatusOk();
        assertThat(segunda).bodyJson().extractingPath("$.total").isEqualTo(6);
        assertThat(segunda).bodyJson().extractingPath("$.totalPaginas").isEqualTo(3);
        assertThat(segunda).bodyJson().extractingPath("$.pagina").isEqualTo(1);
        assertThat(segunda).bodyJson().extractingPath("$.tamanho").isEqualTo(2);
        assertThat(segunda).bodyJson().extractingPath("$.itens.length()").isEqualTo(2);
        assertThat(segunda).bodyJson().extractingPath("$.itens[0].inicio").isEqualTo(instante(2));
        assertThat(segunda).bodyJson().extractingPath("$.itens[1].inicio").isEqualTo(instante(3));
    }

    @Test
    void parametrosDeListagemInvalidosDevolvem400() {
        assertThat(get("/reservas?pagina=-1")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.erros.pagina").isNotNull();
        assertThat(get("/reservas?tamanho=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/reservas?tamanho=101")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/reservas?salaId=abc")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/reservas?de=ontem")).hasStatus(HttpStatus.BAD_REQUEST);
        // período invertido ou vazio
        assertThat(get("/reservas?de={de}&ate={ate}", instante(2), instante(1)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/reservas?de={de}&ate={ate}", instante(1), instante(1)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteCancelaEmVezDeApagarEEIdempotente() {
        String sala = criarSala("Sala Azul", 8);
        String id = criarReserva(sala, "ana", 0, 1);

        assertThat(cancelar(id, "ana")).hasStatus(HttpStatus.NO_CONTENT);
        // a linha continua lá, agora CANCELADA
        assertThat(get("/reservas/{id}", id))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELADA");
        assertThat(get("/reservas?salaId={s}", sala))
                .hasStatusOk().bodyJson().extractingPath("$.itens[0].status").isEqualTo("CANCELADA");
        // cancelar de novo não é erro
        assertThat(cancelar(id, "ana")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void reservaInexistenteDevolve404ParaGetEDelete() {
        assertThat(get("/reservas/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(cancelar(UUID.randomUUID().toString(), "ana")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(cancelar("abc", "ana")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
