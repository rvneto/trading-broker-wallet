package com.rvneto.broker.wallet.service;

import com.rvneto.broker.wallet.domain.Account;
import com.rvneto.broker.wallet.domain.Position;
import com.rvneto.broker.wallet.domain.TransactionType;
import com.rvneto.broker.wallet.domain.WalletTransaction;
import com.rvneto.broker.wallet.dto.OrderEventDTO;
import com.rvneto.broker.wallet.dto.WalletSummaryDTO;
import com.rvneto.broker.wallet.repository.AccountRepository;
import com.rvneto.broker.wallet.repository.PositionRepository;
import com.rvneto.broker.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final WalletTransactionRepository walletTransactionRepository;

    @Value("${app.wallet.default-currency}")
    private String defaultCurrency;

    @Transactional
    public Account deposit(String userId, BigDecimal amount) {
        Account account = accountRepository.findByUserId(userId)
                .orElseGet(() -> Account.builder()
                        .userId(userId)
                        .balance(BigDecimal.ZERO)
                        .currency(defaultCurrency)
                        .build());

        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account withdraw(String userId, BigDecimal amount) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Saldo insuficiente para o saque");
        }

        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalEquity(String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        List<Position> positions = positionRepository.findAllByAccountId(account.getId());

        BigDecimal positionsTotalValue = positions.stream()
                .map(position -> {
                    BigDecimal currentPrice = marketDataService.getLastPrice(position.getTicker());
                    return position.getQuantity().multiply(currentPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return account.getBalance().add(positionsTotalValue);
    }

    @Transactional(readOnly = true)
    public WalletSummaryDTO getWalletSummary(String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        List<Position> positions = positionRepository.findAllByAccountId(account.getId());

        // Soma o valor de cada posição usando o preço real-time do Redis
        BigDecimal positionsValue = positions.stream()
                .map(position -> {
                    BigDecimal currentPrice = marketDataService.getLastPrice(position.getTicker());
                    return position.getQuantity().multiply(currentPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WalletSummaryDTO.builder()
                .userId(userId)
                .availableBalance(account.getBalance())
                .positionsValue(positionsValue)
                .totalEquity(account.getBalance().add(positionsValue))
                .currency(account.getCurrency())
                .build();
    }

    @Transactional
    public void reserveBalance(OrderEventDTO event) {
        log.info("Iniciando reserva de saldo para Ordem ID: {} | Utilizador: {}", event.getOrderId(), event.getUserId());

        // 1. Calcular o montante total da reserva (Quantidade * Preço Limite)
        BigDecimal amountToReserve = event.getPrice().multiply(event.getQuantity());

        // 2. Buscar a conta do utilizador
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada para o utilizador: " + event.getUserId()));

        // 3. Validar se o saldo disponível (balance - blockedBalance) é suficiente
        BigDecimal availableBalance = account.getBalance().subtract(account.getBlockedBalance());

        if (availableBalance.compareTo(amountToReserve) < 0) {
            log.error("Saldo insuficiente. Disponível: {} | Necessário: {}", availableBalance, amountToReserve);
            throw new RuntimeException("Saldo insuficiente para realizar a reserva.");
        }

        // 4. Atualizar o saldo bloqueado
        account.setBlockedBalance(account.getBlockedBalance().add(amountToReserve));
        accountRepository.save(account);

        // 5. Registrar a transação de reserva para auditoria
        WalletTransaction transaction = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.RESERVE)
                .amount(amountToReserve)
                .build();

        walletTransactionRepository.save(transaction);

        log.info("Reserva de R$ {} concluída com sucesso para a ordem {}", amountToReserve, event.getOrderId());
    }

    @Transactional
    public void settleOrder(OrderEventDTO event) {
        log.info("Liquidando ordem FILLED ID: {} para o utilizador: {}", event.getOrderId(), event.getUserId());

        // 1. Calcular valores
        BigDecimal totalReserved = event.getPrice().multiply(new BigDecimal(String.valueOf(event.getQuantity())));
        BigDecimal totalExecuted = event.getExecutedPrice().multiply(new BigDecimal(String.valueOf(event.getQuantity())));

        // 2. Buscar a conta
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));

        // 3. Atualizar saldos
        // Removemos o bloqueio inicial
        account.setBlockedBalance(account.getBlockedBalance().subtract(totalReserved));

        // Subtraímos o valor real do saldo total
        account.setBalance(account.getBalance().subtract(totalExecuted));

        accountRepository.save(account);

        // 4. Registar a transação de liquidação (Settlement)
        WalletTransaction settlementTx = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.SETTLEMENT)
                .amount(totalExecuted.negate()) // Valor negativo pois saiu do património em dinheiro
                .build();
        walletTransactionRepository.save(settlementTx);

        // 5. Atualizar Carteira de Ativos (Ações)
        updatePosition(event, account);

        log.info("Liquidação concluída. Saldo Total: {} | Bloqueado: {}",
                account.getBalance(), account.getBlockedBalance());
    }

    private void updatePosition(OrderEventDTO event, Account account) {
        Position asset = positionRepository.findByAccountIdAndTicker(account.getId(), event.getTicker())
                .orElse(Position.builder()
                        .account(account)
                        .ticker(event.getTicker())
                        .quantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .build());

        // Lógica de Preço Médio: (Custo Atual + Custo Novo) / (Qtd Atual + Qtd Nova)
        BigDecimal currentCost = asset.getAveragePrice().multiply(asset.getQuantity());
        BigDecimal newCost = event.getExecutedPrice().multiply(new BigDecimal(String.valueOf(event.getQuantity())));

        BigDecimal totalQuantity = asset.getQuantity().add(new BigDecimal(String.valueOf(event.getQuantity())));
        BigDecimal newAveragePrice = currentCost.add(newCost)
                .divide(totalQuantity, 4, RoundingMode.HALF_UP);

        asset.setQuantity(totalQuantity);
        asset.setAveragePrice(newAveragePrice);

        positionRepository.save(asset);
        log.info("Ativos atualizados: {} unidades de {} a preço médio de {}", totalQuantity, event.getTicker(), newAveragePrice);
    }

    @Transactional
    public void refundBalance(OrderEventDTO event) {
        log.info("Processando estorno (REFUND) para ordem REJECTED ID: {}", event.getOrderId());

        // 1. Calcular o valor que foi originalmente bloqueado
        BigDecimal amountToRefund = event.getPrice().multiply(event.getQuantity());

        // 2. Buscar a conta
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada para estorno"));

        // 3. Devolver o saldo bloqueado para o disponível
        // IMPORTANTE: Apenas subtraímos do blockedBalance.
        // O 'balance' principal permanece intacto pois o dinheiro nunca saiu de lá.
        if (account.getBlockedBalance().compareTo(amountToRefund) >= 0) {
            account.setBlockedBalance(account.getBlockedBalance().subtract(amountToRefund));
        } else {
            log.warn("Tentativa de estorno maior que o saldo bloqueado para ordem {}", event.getOrderId());
            account.setBlockedBalance(BigDecimal.ZERO); // Prevenção de saldo negativo
        }

        accountRepository.save(account);

        // 4. Registrar a transação de estorno para histórico
        WalletTransaction refundTx = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.REFUND)
                .amount(amountToRefund) // Valor positivo aqui indica "devolução" ao disponível
                .build();

        walletTransactionRepository.save(refundTx);

        log.info("Estorno de R$ {} concluído. Novo saldo disponível: {}",
                amountToRefund, account.getAvailableBalance());
    }
}