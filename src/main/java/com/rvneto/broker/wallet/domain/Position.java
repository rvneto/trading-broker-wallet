package com.rvneto.broker.wallet.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String ticker;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(name = "average_price", precision = 19, scale = 4, nullable = false)
    private BigDecimal averagePrice;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
