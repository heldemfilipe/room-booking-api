-- Etapa 3, solução (b): lock otimista. A versão mora na SALA, e não na reserva: numa corrida de INSERÇÃO não existe
-- linha de reserva para versionar. Toda reserva nova força o incremento da versão da sala (OPTIMISTIC_FORCE_INCREMENT);
-- duas transações que leram a mesma versão disputam o UPDATE ... WHERE versao = ?, e só uma o consegue.
ALTER TABLE salas ADD COLUMN versao bigint NOT NULL DEFAULT 0;
