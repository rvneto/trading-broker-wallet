CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL UNIQUE, -- Identificador único do cliente
    balance DECIMAL(19, 4) DEFAULT 0.0000,
    currency VARCHAR(10) DEFAULT 'BRL',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    quantity DECIMAL(19, 4) NOT NULL,
    average_price DECIMAL(19, 4) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_position FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT uq_account_symbol UNIQUE (account_id, ticker) -- Garante uma linha por ativo por conta
);