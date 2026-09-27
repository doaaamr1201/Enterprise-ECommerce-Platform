package com.microservices.pro.orderservice.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryLoggerTest {

    private final RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .build());

    @Test
    void retry_shouldCallPaymentThreeTimesThenGiveUp() {
        new RetryLogger(registry).attachRetryListeners();
        Retry retry = registry.retry("paymentService");
        List<Integer> retryAttempts = new ArrayList<>();
        retry.getEventPublisher().onRetry(e -> retryAttempts.add(e.getNumberOfRetryAttempts()));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry.executeSupplier(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Payment Service unavailable");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(retryAttempts).containsExactly(1, 2);
    }

    @Test
    void retry_shouldStopAsSoonAsPaymentSucceeds() {
        new RetryLogger(registry).attachRetryListeners();
        Retry retry = registry.retry("paymentService");
        AtomicInteger calls = new AtomicInteger();

        String result = retry.executeSupplier(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
            return "Payment successful";
        });

        assertThat(result).isEqualTo("Payment successful");
        assertThat(calls.get()).isEqualTo(2);
    }
}
