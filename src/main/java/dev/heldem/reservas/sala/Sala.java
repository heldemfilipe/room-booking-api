package dev.heldem.reservas.sala;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salas")
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private int capacidade;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    protected Sala() {
        // exigido pelo JPA
    }

    public Sala(String nome, int capacidade, Instant agora) {
        this.nome = nome;
        this.capacidade = capacidade;
        this.criadaEm = agora;
    }

    public void atualizar(String nome, int capacidade) {
        this.nome = nome;
        this.capacidade = capacidade;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
