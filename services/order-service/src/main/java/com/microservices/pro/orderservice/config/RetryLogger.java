package com.microservices.pro.orderservice.config;

import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryLogger {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void attachRetryListeners() {
        retryRegistry.retry("paymentService").getEventPublisher()
                .onRetry(e -> log.warn("[RETRY] Attempt #{} -- {}",
                        e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                .onSuccess(e -> log.info("[RETRY] Succeeded after {} attempt(s)",
                        e.getNumberOfRetryAttempts()));
    }
}
