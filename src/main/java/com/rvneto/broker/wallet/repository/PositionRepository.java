package com.rvneto.broker.wallet.repository;

import com.rvneto.broker.wallet.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findAllByAccountId(Long accountId);

    Optional<Position> findByAccountIdAndTicker(Long accountId, String ticker);

}