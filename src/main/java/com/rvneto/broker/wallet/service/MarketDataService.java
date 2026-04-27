package com.rvneto.broker.wallet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final RedisTemplate<String, Object> redisTemplate;

    public BigDecimal getLastPrice(String ticker) {
        String key = "ticker:price:" + ticker.toUpperCase();

        // O Python salva como um Hash. Vamos buscar o campo "price"
        Object priceObj = redisTemplate.opsForHash().get(key, "price");

        if (isNull(priceObj)) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(priceObj.toString());
    }
}
