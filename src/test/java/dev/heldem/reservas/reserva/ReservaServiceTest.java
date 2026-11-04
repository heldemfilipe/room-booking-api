package dev.heldem.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import dev.heldem.reservas.comum.ConflitoException;
import dev.heldem.reservas.reserva.dto.CriarReservaRequest;
import dev.heldem.reservas.reserva.idempotencia.ChaveIdempotenciaRepository;
import dev.heldem.reservas.sala.Sala;
import dev.heldem.reservas.sala.SalaService;

/**
 * A tradução do erro do banco em 409. Com o lock da sala, a consulta de sobreposição pega quase todos os perdedores, então
 * a constraint quase nunca chega a recusar nos testes de corrida; aqui o banco "falha" de propósito, no ponto exato do
 * saveAndFlush, com o SQLState que o PostgreSQL de verdade devolve.
 */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-04T12:00:00Z");

    @Mock
    ReservaRepository repository;

    @Mock
    ChaveIdempotenciaRepository chaves;

    @Mock
    SalaService salaService;

    ReservaService service;

    Sala sala = new Sala("Sala Azul", 8, AGORA);
    UUID salaId = UUID.randomUUID();
    CriarReservaRequest request = new CriarReservaRequest(salaId, AGORA.plusSeconds(3600), AGORA.plusSeconds(7200));

    @BeforeEach
    void montar() {
        service = new ReservaService(repository, chaves, salaService, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(salaService.buscarParaReservar(salaId)).thenReturn(sala);
        when(repository.existeSobreposicao(any(), any(), any())).thenReturn(false); // a consulta não vê o conflito
    }

    private static DataIntegrityViolationException comSqlState(String estado) {
        return new DataIntegrityViolationException("falha do banco", new SQLException("do driver", estado));
    }

    @Test
    void violacaoDaExclusionConstraintVira409() {
        when(repository.saveAndFlush(any(Reserva.class))).thenThrow(comSqlState("23P01"));

        assertThatThrownBy(() -> service.criar("ana", request))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("já está reservada");
    }

    @Test
    void deadlockDetectadoPeloBancoVira409() {
        when(repository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new CannotAcquireLockException("deadlock", new SQLException("deadlock detected", "40P01")));

        assertThatThrownBy(() -> service.criar("ana", request))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ao mesmo tempo");
    }

    @Test
    void oSqlStateEProcuradoNaCadeiaDeCausas() {
        // o driver costuma vir embrulhado em várias camadas (Hibernate, Spring): o estado está na causa da causa
        var fundo = new RuntimeException("camada do Hibernate", new SQLException("exclusion", "23P01"));
        when(repository.saveAndFlush(any(Reserva.class)))
                .thenThrow(new DataIntegrityViolationException("camada do Spring", fundo));

        assertThatThrownBy(() -> service.criar("ana", request)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void outrosErrosDoBancoNaoSaoEngolidosComoConflito() {
        // uma falha de conexão não é conflito de reserva: tem que continuar sendo um erro de verdade (500)
        var queda = new DataAccessResourceFailureException("sem conexão", new SQLException("caiu", "08006"));
        when(repository.saveAndFlush(any(Reserva.class))).thenThrow(queda);

        assertThatThrownBy(() -> service.criar("ana", request)).isSameAs(queda);
        // e uma violação de OUTRA constraint (aqui, NOT NULL) também não vira "sala ocupada"
        var outra = comSqlState("23502");
        when(repository.saveAndFlush(any(Reserva.class))).thenThrow(outra);
        assertThatThrownBy(() -> service.criar("ana", request)).isSameAs(outra);
    }

    @Test
    void consultaQueJaEnxergaOConflitoDevolve409SemTocarNoBancoDeNovo() {
        when(repository.existeSobreposicao(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.criar("ana", request)).isInstanceOf(ConflitoException.class);

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
        assertThat(sala.getNome()).isEqualTo("Sala Azul");
    }
}
