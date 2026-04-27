package com.rvneto.broker.wallet.domain;

public enum TransactionType {
    RESERVE,    // Bloqueio inicial ao criar a ordem
    SETTLEMENT, // Débito final após execução (FILLED)
    REFUND      // Estorno após rejeição (REJECTED)
}