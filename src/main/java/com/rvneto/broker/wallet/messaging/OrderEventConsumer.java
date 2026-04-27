package com.rvneto.broker.wallet.messaging;

import com.rvneto.broker.wallet.dto.OrderEventDTO;
import com.rvneto.broker.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final WalletService walletService;

    @KafkaListener(
            topics = "${app.kafka.consumer.topic-order-events}",
            groupId = "broker-wallet-api"
    )
    public void consume(OrderEventDTO event) {
        log.info("Order event received: ID {} | Status {} | User {}", event.getOrderId(), event.getStatus(), event.getUserId());
        try {
            switch (event.getStatus()) {
                case "PENDING" -> walletService.reserveBalance(event);
                case "FILLED" -> walletService.settleOrder(event);
                case "REJECTED" -> walletService.refundBalance(event);
                default -> log.warn("Event with status {} not handled by Wallet", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Critical error processing financial event for order {}: {}", event.getOrderId(), e.getMessage(), e);
            // Rethrow so Kafka can apply retry/DLQ policy instead of silently discarding
            throw e;
        }
    }
}