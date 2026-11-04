-- Salas que podem ser reservadas. O Hibernate só valida este schema (spring.jpa.hibernate.ddl-auto=validate).

CREATE TABLE salas (
    id         uuid                        PRIMARY KEY,
    nome       varchar(100)                NOT NULL,
    capacidade integer                     NOT NULL,
    criada_em  timestamp(6) with time zone NOT NULL,
    CONSTRAINT ck_salas_capacidade CHECK (capacidade > 0)
);

-- o nome é único sem diferenciar maiúsculas ("Sala A" e "sala a" são a mesma sala)
CREATE UNIQUE INDEX uk_salas_nome ON salas (lower(nome));
