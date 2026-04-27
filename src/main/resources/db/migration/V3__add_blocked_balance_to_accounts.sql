-- Adiciona o campo de saldo bloqueado com valor padrão zero
ALTER TABLE accounts
    ADD COLUMN blocked_balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000 AFTER balance;

-- Opcional: Adicionar um comentário para clareza
ALTER TABLE accounts
    MODIFY COLUMN blocked_balance DECIMAL (19, 4) NOT NULL DEFAULT 0.0000
    COMMENT 'Valor reservado para ordens PENDING que ainda não foram liquidadas';