package com.rvneto.broker.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WalletSummaryDTO {

    private String userId;
    private BigDecimal availableBalance; // Saldo em conta (MySQL)
    private BigDecimal positionsValue;   // Valor total das ações (Redis)
    private BigDecimal totalEquity;      // Soma dos dois
    private String currency;

}