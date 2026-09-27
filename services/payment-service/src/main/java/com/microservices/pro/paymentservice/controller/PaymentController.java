package com.microservices.pro.paymentservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final Random random = new Random();

    @Value("${payment.failure-rate:50}")
    private int failureRate;

    @Value("${payment.delay-ms:0}")
    private long delayMs;

    @PostMapping
    public String processPayment() {

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Payment interrupted", e);
            }
        }

        if (random.nextInt(100) < failureRate) {
            throw new RuntimeException("Payment Service unavailable");
        }

        return "Payment successful";
    }
}