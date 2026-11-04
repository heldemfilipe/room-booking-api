-- Reservas de salas. Nesta versão o banco ainda NÃO impede reservas que se sobrepõem: isso é a Etapa 3
-- (concorrência), onde a corrida é reproduzida e depois fechada.

CREATE TABLE reservas (
    id         uuid                        PRIMARY KEY,
    sala_id    uuid                        NOT NULL REFERENCES salas (id),
    -- o "sub" do JWT (Keycloak); o usuário em si só existe no Keycloak
    usuario_id varchar(64)                 NOT NULL,
    inicio     timestamp(6) with time zone NOT NULL,
    fim        timestamp(6) with time zone NOT NULL,
    status     varchar(20)                 NOT NULL,
    criada_em  timestamp(6) with time zone NOT NULL,
    CONSTRAINT ck_reservas_intervalo CHECK (fim > inicio),
    CONSTRAINT ck_reservas_status CHECK (status IN ('ATIVA', 'CANCELADA'))
);

-- GET /reservas filtra por sala e período: sala_id primeiro, depois o início
CREATE INDEX idx_reservas_sala_inicio ON reservas (sala_id, inicio);
CREATE INDEX idx_reservas_usuario ON reservas (usuario_id);
