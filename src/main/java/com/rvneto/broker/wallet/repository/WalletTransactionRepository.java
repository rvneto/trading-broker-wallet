package com.rvneto.broker.wallet.repository;

import com.rvneto.broker.wallet.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByOrderId(Long orderId);

}
