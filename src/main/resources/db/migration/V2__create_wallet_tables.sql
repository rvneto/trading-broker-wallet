-- Tabela de Transações Financeiras (Log de Auditoria)
CREATE TABLE wallet_transactions
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          VARCHAR(100)   NOT NULL,
    order_id         BIGINT         NOT NULL,
    transaction_type ENUM('RESERVE', 'SETTLEMENT', 'REFUND') NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_tx_user ON wallet_transactions (user_id);