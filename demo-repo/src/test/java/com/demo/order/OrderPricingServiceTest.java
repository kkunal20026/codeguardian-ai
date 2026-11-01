package com.demo.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Author: Kumar.Kunal
 * Project: codeguardian-ai
 * File: OrderPricingServiceTest.java
 * Date: October 31, 2026
 */
class OrderPricingServiceTest {

    private final OrderPricingService service = new OrderPricingService();

    @Test
    void calculatesBasePrice() {
        assertEquals(new BigDecimal("30.00"), service.price(new BigDecimal("10.00"), 3));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> service.price(BigDecimal.ONE, -1));
    }

    @Test
    void roundsToTwoDecimals() {
        assertEquals(new BigDecimal("1.00"), service.price(new BigDecimal("0.333"), 3));
    }

    @Test
    void appliesNoDiscountOnWeekday() {
        assertEquals(new BigDecimal("100.00"), service.applyDiscount(new BigDecimal("100.00"), DayOfWeek.MONDAY));
    }

    // Flaky on purpose: the pricing engine takes 50-300 ms, the test only waits 200 ms.
    @Test
    void appliesDiscountWhenPriceIsReady() throws Exception {
        CompletableFuture<BigDecimal> price = service.priceAsync(new BigDecimal("100.00"));
        Thread.sleep(200);
        assertTrue(price.isDone(), "price should be ready");
        assertEquals(new BigDecimal("90.00"), price.get());
    }
}
