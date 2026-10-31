package com.codehealth.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryTest {

    @Test
    void retriesTransientFailuresThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        Retry.Outcome<String> outcome = Retry.call("op", 4, Duration.ofMillis(1), () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("connection reset");
            }
            return "ok";
        });
        assertThat(outcome.value()).isEqualTo("ok");
        assertThat(outcome.attempts()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> Retry.call("op", 4, Duration.ofMillis(1), () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.FORBIDDEN, "no", null, null, null);
        })).isInstanceOf(HttpClientErrorException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void reportsAttemptsWhenExhausted() {
        assertThatThrownBy(() -> Retry.call("op", 3, Duration.ofMillis(1), () -> {
            throw new ResourceAccessException("down");
        })).isInstanceOfSatisfying(Retry.RetryExhaustedException.class, ex -> assertThat(ex.attempts()).isEqualTo(3));
    }

    @Test
    void backoffGrowsAndIsCapped() {
        assertThat(Retry.backoffMillis(1, Duration.ofMillis(500))).isBetween(250L, 500L);
        assertThat(Retry.backoffMillis(10, Duration.ofMillis(500))).isBetween(4000L, 8000L);
    }
}
