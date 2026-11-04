-- Idempotência do POST /reservas (header Idempotency-Key). Repetir a mesma requisição (clique duplo, retentativa depois de
-- um timeout) não pode criar duas reservas.
--
-- A UNIQUE (usuario_id, chave) é o que fecha a corrida de duas requisições com a MESMA chave ao mesmo tempo: a chave é
-- inserida ANTES da reserva, na mesma transação, com ON CONFLICT DO NOTHING. A segunda inserção espera a primeira
-- confirmar (ou desfazer) e só então decide: se a chave já existe, devolve a resposta da primeira.
CREATE TABLE chaves_idempotencia (
    id              uuid                        PRIMARY KEY,
    -- a chave vale por usuário: duas pessoas podem usar o mesmo texto sem se enxergar
    usuario_id      varchar(64)                 NOT NULL,
    chave           varchar(100)                NOT NULL,
    -- SHA-256 (hex) de sala + início + fim: a mesma chave com outro corpo é um erro do cliente (422), não um replay
    hash_requisicao varchar(64)                 NOT NULL,
    -- preenchida na mesma transação em que a reserva é criada; nula só durante essa transação
    reserva_id      uuid                        REFERENCES reservas (id),
    criada_em       timestamp(6) with time zone NOT NULL,
    CONSTRAINT uk_idempotencia_usuario_chave UNIQUE (usuario_id, chave)
);
