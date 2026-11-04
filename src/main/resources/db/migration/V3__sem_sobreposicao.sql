-- Etapa 3, solução (a): o banco garante que duas reservas ATIVAS da mesma sala nunca se sobrepõem.
--
-- Uma exclusion constraint é como uma UNIQUE generalizada: em vez de "igual", usa um operador. Aqui, "mesma sala" (=)
-- E "intervalos que se sobrepõem" (&&) não podem ocorrer juntos em duas linhas. O PostgreSQL checa isso no índice, no
-- momento do insert, e uma transação concorrente que conflita espera pela outra confirmar ou desfazer: não há janela de
-- corrida, ao contrário de "consultar e depois inserir".

-- Precisa da extensão btree_gist para o índice GiST aceitar o operador = em sala_id (uuid) junto de && no intervalo.
-- Ela é "trusted" no PostgreSQL 13+, então o dono do banco a cria sem ser superusuário.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- '[)': fechado no início e aberto no fim, como a aplicação trata os horários: 10h-11h e 11h-12h NÃO se sobrepõem.
-- WHERE status = 'ATIVA': uma reserva CANCELADA libera o horário (a linha continua lá, mas fora da restrição).
ALTER TABLE reservas
    ADD CONSTRAINT ex_reservas_sem_sobreposicao
    EXCLUDE USING gist (sala_id WITH =, tstzrange(inicio, fim, '[)') WITH &&)
    WHERE (status = 'ATIVA');
