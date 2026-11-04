package dev.heldem.reservas.reserva.idempotencia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma chave de idempotência já usada, com a reserva que ela criou. Só leitura aqui; a escrita é por SQL nativo. */
@Entity
@Table(name = "chaves_idempotencia")
public class ChaveIdempotencia {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false, length = 64)
    private String usuarioId;

    @Column(nullable = false, length = 100)
    private String chave;

    @Column(name = "hash_requisicao", nullable = false, length = 64)
    private String hashRequisicao;

    @Column(name = "reserva_id")
    private UUID reservaId;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected ChaveIdempotencia() {
        // exigido pelo JPA
    }

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}
