package com.demo.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class OrderPricingService {

    private static final BigDecimal WEEKEND_DISCOUNT = new BigDecimal("0.90");

    public BigDecimal price(BigDecimal unitPrice, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal applyDiscount(BigDecimal amount, DayOfWeek day) {
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        return (weekend ? amount.multiply(WEEKEND_DISCOUNT) : amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Simulates a call to a pricing engine whose latency varies between 50 and 300 ms.
     */
    public CompletableFuture<BigDecimal> priceAsync(BigDecimal amount) {
        long latencyMs = ThreadLocalRandom.current().nextLong(50, 300);
        return CompletableFuture.supplyAsync(() -> applyDiscount(amount, DayOfWeek.SATURDAY),
                CompletableFuture.delayedExecutor(latencyMs, TimeUnit.MILLISECONDS));
    }
}
