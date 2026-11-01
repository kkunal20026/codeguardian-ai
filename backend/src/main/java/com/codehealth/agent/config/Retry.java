package com.codehealth.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: Retry.java
 *  Date: November 1, 2026
 * Retry with exponential backoff and full jitter for outbound calls.
 * <p>
 * Client errors (4xx) are not retried: repeating a bad request or a missing permission never helps,
 * and retrying a 409/422 on a create call could hide a real conflict.
 */
public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    public static final int DEFAULT_ATTEMPTS = 4;
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_DELAY = Duration.ofSeconds(8);

    private Retry() {
    }

    public record Outcome<T>(T value, int attempts) {
    }

    public static <T> Outcome<T> call(String operation, Supplier<T> action) {
        return call(operation, DEFAULT_ATTEMPTS, DEFAULT_INITIAL_DELAY, action);
    }

    public static <T> Outcome<T> call(String operation, int maxAttempts, Duration initialDelay, Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new Outcome<>(action.get(), attempt);
            } catch (HttpClientErrorException httpClientErrorException) {
                throw httpClientErrorException;
            } catch (RuntimeException runtimeException) {
                last = runtimeException;
                if (attempt == maxAttempts) {
                    break;
                }
                long delayMs = backoffMillis(attempt, initialDelay);
                log.warn("{} failed on attempt {}/{}: {}. Retrying in {} ms", operation, attempt, maxAttempts, runtimeException.getMessage(), delayMs);
                sleep(delayMs);
            }
        }
        throw new RetryExhaustedException(operation, maxAttempts, last);
    }

    /**
     * Full jitter: random value between 0 and min(cap, initial * 2^(attempt-1)).
     */
    static long backoffMillis(int attempt, Duration initialDelay) {
        long exponential = initialDelay.toMillis() * (1L << Math.min(attempt - 1, 20));
        long capped = Math.min(exponential, MAX_DELAY.toMillis());
        return ThreadLocalRandom.current().nextLong(capped / 2, capped + 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", interruptedException);
        }
    }

    public static final class RetryExhaustedException extends RuntimeException {

        private final int attempts;

        RetryExhaustedException(String operation, int attempts, Throwable throwableCause) {
            super(operation + " failed after " + attempts + " attempts: "
                    + (throwableCause == null ? "unknown" : throwableCause.getMessage()), throwableCause);
            this.attempts = attempts;
        }

        public int attempts() {
            return attempts;
        }
    }
}