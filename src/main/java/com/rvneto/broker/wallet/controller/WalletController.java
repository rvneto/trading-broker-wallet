package com.rvneto.broker.wallet.controller;

import com.rvneto.broker.wallet.domain.Account;
import com.rvneto.broker.wallet.domain.Position;
import com.rvneto.broker.wallet.domain.WalletTransaction;
import com.rvneto.broker.wallet.dto.WalletSummaryDTO;
import com.rvneto.broker.wallet.repository.PositionRepository;
import com.rvneto.broker.wallet.repository.AccountRepository;
import com.rvneto.broker.wallet.repository.WalletTransactionRepository;
import com.rvneto.broker.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Endpoints for balance and portfolio management")
public class WalletController {

    private final WalletService walletService;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Operation(summary = "Deposit funds into account",
               description = "Creates the account on first deposit if it does not exist yet")
    @PostMapping("/{userId}/deposit")
    public ResponseEntity<Account> deposit(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Amount to deposit") @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.deposit(userId, amount));
    }

    @Operation(summary = "Withdraw funds from account",
               description = "Only withdraws from available balance (total balance minus blocked balance)")
    @PostMapping("/{userId}/withdraw")
    public ResponseEntity<Account> withdraw(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Amount to withdraw") @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.withdraw(userId, amount));
    }

    @Operation(summary = "Get consolidated wallet summary",
               description = "Returns available balance, real-time position values and total equity")
    @GetMapping("/{userId}/summary")
    public ResponseEntity<WalletSummaryDTO> getWalletSummary(
            @Parameter(description = "User ID") @PathVariable String userId) {
        return ResponseEntity.ok(walletService.getWalletSummary(userId));
    }

    @Operation(summary = "List asset positions",
               description = "Returns all stock positions held by the user")
    @GetMapping("/{userId}/positions")
    public ResponseEntity<List<Position>> getPositions(
            @Parameter(description = "User ID") @PathVariable String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return ResponseEntity.ok(positionRepository.findAllByAccountId(account.getId()));
    }

    @Operation(summary = "List transaction history",
               description = "Returns all financial transactions (RESERVE, SETTLEMENT, REFUND) for the user")
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<WalletTransaction>> getTransactions(
            @Parameter(description = "User ID") @PathVariable String userId) {
        return ResponseEntity.ok(walletTransactionRepository.findByUserId(userId));
    }
}