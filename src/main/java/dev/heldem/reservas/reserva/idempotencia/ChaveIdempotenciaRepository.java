package dev.heldem.reservas.reserva.idempotencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, UUID> {

    /**
     * Registra a chave, se ainda não existir. Devolve 1 se esta transação a criou, ou 0 se já existia. Se outra transação
     * está criando a MESMA chave agora, esta espera ela confirmar ou desfazer (é o índice único que serializa): por isso
     * duas requisições simultâneas com a mesma chave nunca criam duas reservas.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into chaves_idempotencia (id, usuario_id, chave, hash_requisicao, criada_em)
            values (:id, :usuarioId, :chave, :hash, :criadaEm)
            on conflict (usuario_id, chave) do nothing
            """)
    int inserirSeAusente(@Param("id") UUID id, @Param("usuarioId") String usuarioId, @Param("chave") String chave,
            @Param("hash") String hash, @Param("criadaEm") Instant criadaEm);

    @Modifying
    @Query(nativeQuery = true, value = """
            update chaves_idempotencia set reserva_id = :reservaId
            where usuario_id = :usuarioId and chave = :chave
            """)
    int definirReserva(@Param("usuarioId") String usuarioId, @Param("chave") String chave,
            @Param("reservaId") UUID reservaId);

    Optional<ChaveIdempotencia> findByUsuarioIdAndChave(String usuarioId, String chave);
}
