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
                        .blockedBalance(BigDecimal.ZERO) // fix: initialize to avoid NPE in getAvailableBalance()
                        .currency(defaultCurrency)
                        .build());

        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account withdraw(String userId, BigDecimal amount) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // fix: validate available balance (balance - blockedBalance), not total balance
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient available balance for withdrawal");
        }

        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalEquity(String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

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
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Position> positions = positionRepository.findAllByAccountId(account.getId());

        // Sum position values using real-time prices from Redis
        BigDecimal positionsValue = positions.stream()
                .map(position -> {
                    BigDecimal currentPrice = marketDataService.getLastPrice(position.getTicker());
                    return position.getQuantity().multiply(currentPrice);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return WalletSummaryDTO.builder()
                .userId(userId)
                .availableBalance(account.getAvailableBalance()) // fix: use available balance, not total balance
                .positionsValue(positionsValue)
                .totalEquity(account.getBalance().add(positionsValue))
                .currency(account.getCurrency())
                .build();
    }

    @Transactional
    public void reserveBalance(OrderEventDTO event) {
        log.info("Starting balance reserve for Order ID: {} | User: {}", event.getOrderId(), event.getUserId());

        // 1. Calculate total reserve amount (Quantity * Limit Price)
        BigDecimal amountToReserve = event.getPrice().multiply(event.getQuantity());

        // 2. Fetch user account
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Account not found for user: " + event.getUserId()));

        // 3. Validate available balance (balance - blockedBalance)
        BigDecimal availableBalance = account.getAvailableBalance();

        if (availableBalance.compareTo(amountToReserve) < 0) {
            log.error("Insufficient balance. Available: {} | Required: {}", availableBalance, amountToReserve);
            throw new RuntimeException("Insufficient balance to reserve funds.");
        }

        // 4. Update blocked balance
        account.setBlockedBalance(account.getBlockedBalance().add(amountToReserve));
        accountRepository.save(account);

        // 5. Record reserve transaction for audit
        WalletTransaction transaction = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.RESERVE)
                .amount(amountToReserve)
                .build();

        walletTransactionRepository.save(transaction);

        log.info("Reserve of R$ {} completed successfully for order {}", amountToReserve, event.getOrderId());
    }

    @Transactional
    public void settleOrder(OrderEventDTO event) {
        log.info("Settling FILLED order ID: {} for user: {}", event.getOrderId(), event.getUserId());

        // 1. Calculate reserved and executed amounts
        BigDecimal totalReserved = event.getPrice().multiply(event.getQuantity());
        BigDecimal totalExecuted = event.getExecutedPrice().multiply(event.getQuantity());

        // 2. Fetch account
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // 3. Release the initial block — fix: guard against negative blockedBalance
        BigDecimal newBlockedBalance = account.getBlockedBalance().subtract(totalReserved);
        account.setBlockedBalance(newBlockedBalance.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO
                : newBlockedBalance);

        // 4. Debit the actual executed amount from total balance
        account.setBalance(account.getBalance().subtract(totalExecuted));
        accountRepository.save(account);

        // 5. Record settlement transaction (negative amount = funds left the account)
        WalletTransaction settlementTx = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.SETTLEMENT)
                .amount(totalExecuted.negate())
                .build();
        walletTransactionRepository.save(settlementTx);

        // 6. Update asset position
        updatePosition(event, account);

        log.info("Settlement complete. Total balance: {} | Blocked: {}",
                account.getBalance(), account.getBlockedBalance());
    }

    private void updatePosition(OrderEventDTO event, Account account) {
        Position position = positionRepository.findByAccountIdAndTicker(account.getId(), event.getTicker())
                .orElse(Position.builder()
                        .account(account)
                        .ticker(event.getTicker())
                        .quantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .build());

        boolean isSell = "SELL".equalsIgnoreCase(event.getSide());

        if (isSell) {
            // fix: SELL reduces position quantity
            BigDecimal newQuantity = position.getQuantity().subtract(event.getQuantity());

            if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                // Position fully closed — remove it
                if (position.getId() != null) {
                    positionRepository.delete(position);
                    log.info("Position fully closed and removed: {}", event.getTicker());
                }
                return;
            }

            position.setQuantity(newQuantity);
            // Average price stays the same on sell — only quantity changes
        } else {
            // BUY: weighted average price calculation
            // New Average = (Current Cost + New Cost) / (Current Qty + New Qty)
            BigDecimal currentCost = position.getAveragePrice().multiply(position.getQuantity());
            BigDecimal newCost = event.getExecutedPrice().multiply(event.getQuantity());
            BigDecimal totalQuantity = position.getQuantity().add(event.getQuantity());
            BigDecimal newAveragePrice = currentCost.add(newCost)
                    .divide(totalQuantity, 4, RoundingMode.HALF_UP);

            position.setQuantity(totalQuantity);
            position.setAveragePrice(newAveragePrice);
        }

        positionRepository.save(position);
        log.info("Position updated: {} units of {} at average price {}",
                position.getQuantity(), event.getTicker(), position.getAveragePrice());
    }

    @Transactional
    public void refundBalance(OrderEventDTO event) {
        log.info("Processing refund (REFUND) for REJECTED order ID: {}", event.getOrderId());

        // 1. Calculate the amount that was originally blocked
        BigDecimal amountToRefund = event.getPrice().multiply(event.getQuantity());

        // 2. Fetch account
        Account account = accountRepository.findByUserId(event.getUserId())
                .orElseThrow(() -> new RuntimeException("Account not found for refund"));

        // 3. Release blocked balance back to available
        // IMPORTANT: only subtract from blockedBalance — total balance was never touched
        if (account.getBlockedBalance().compareTo(amountToRefund) >= 0) {
            account.setBlockedBalance(account.getBlockedBalance().subtract(amountToRefund));
        } else {
            log.warn("Refund amount exceeds blocked balance for order {}", event.getOrderId());
            account.setBlockedBalance(BigDecimal.ZERO); // prevent negative blocked balance
        }

        accountRepository.save(account);

        // 4. Record refund transaction for audit history
        WalletTransaction refundTx = WalletTransaction.builder()
                .userId(event.getUserId())
                .orderId(event.getOrderId())
                .transactionType(TransactionType.REFUND)
                .amount(amountToRefund) // positive amount indicates funds returned to available
                .build();

        walletTransactionRepository.save(refundTx);

        log.info("Refund of R$ {} completed. New available balance: {}",
                amountToRefund, account.getAvailableBalance());
    }
}