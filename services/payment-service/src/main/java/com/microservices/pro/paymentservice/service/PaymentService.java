package com.microservices.pro.paymentservice.service;

import com.microservices.pro.paymentservice.exception.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Service
public class PaymentService {

    private final Random random = new Random();
    private final int failureRate;
    private final long delayMs;

    public PaymentService(
            @Value("${payment.failure-rate:50}") int failureRate,
            @Value("${payment.delay-ms:0}") long delayMs
    ) {
        this.failureRate = failureRate;
        this.delayMs = delayMs;
    }

    public String processPayment() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentException("Payment interrupted");
            }
        }

        if (random.nextInt(100) < failureRate) {
            throw new PaymentException("Payment Service unavailable");
        }

        return "TXN-" + UUID.randomUUID();
    }
}
