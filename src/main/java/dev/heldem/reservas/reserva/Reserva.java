package dev.heldem.reservas.reserva;

import java.time.Instant;
import java.util.UUID;

import dev.heldem.reservas.sala.Sala;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_id", nullable = false)
    private Sala sala;

    // o "sub" do JWT; o usuário em si só existe no Keycloak
    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusReserva status;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    protected Reserva() {
        // exigido pelo JPA
    }

    /** Toda reserva nasce ATIVA. */
    public Reserva(Sala sala, String usuarioId, Instant inicio, Instant fim, Instant agora) {
        this.sala = sala;
        this.usuarioId = usuarioId;
        this.inicio = inicio;
        this.fim = fim;
        this.status = StatusReserva.ATIVA;
        this.criadaEm = agora;
    }

    /** Cancelar é idempotente: cancelar de novo uma reserva cancelada não muda nada. */
    public void cancelar() {
        this.status = StatusReserva.CANCELADA;
    }

    public UUID getId() {
        return id;
    }

    public Sala getSala() {
        return sala;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
