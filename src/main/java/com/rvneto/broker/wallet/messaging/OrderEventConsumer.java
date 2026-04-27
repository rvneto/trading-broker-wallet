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
        try {
            switch (event.getStatus()) {
                case "PENDING" -> walletService.reserveBalance(event);
                case "FILLED" -> walletService.settleOrder(event);
                case "REJECTED" -> walletService.refundBalance(event);
                default -> log.info("Evento com status {} ignorado pela Wallet", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Erro crítico ao processar evento financeiro da ordem {}: {}", event.getOrderId(), e.getMessage());
        }
    }
}