package com.rvneto.broker.wallet.controller;

import com.rvneto.broker.wallet.domain.Account;
import com.rvneto.broker.wallet.dto.WalletSummaryDTO;
import com.rvneto.broker.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Endpoints para gestão de saldo e patrimônio")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Realiza um depósito em conta")
    @PostMapping("/{userId}/deposit")
    public ResponseEntity<Account> deposit(@PathVariable String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.deposit(userId, amount));
    }

    @Operation(summary = "Consulta o resumo consolidado da carteira (Saldo + Ativos)")
    @GetMapping("/{userId}/summary")
    public ResponseEntity<WalletSummaryDTO> getWalletSummary(@PathVariable String userId) {
        return ResponseEntity.ok(walletService.getWalletSummary(userId));
    }
}