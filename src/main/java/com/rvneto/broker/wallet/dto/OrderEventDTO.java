package com.rvneto.broker.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderEventDTO {

    private Long orderId;
    private String userId;
    private String ticker;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal executedPrice;
    private String side;
    private String status;
    private LocalDateTime eventTimestamp;

}