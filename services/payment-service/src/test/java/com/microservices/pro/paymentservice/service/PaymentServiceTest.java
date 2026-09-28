package com.microservices.pro.paymentservice.service;

import com.microservices.pro.paymentservice.exception.PaymentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    @Test
    void processPayment_withZeroFailureRate_shouldReturnTransactionId() {
        assertThat(new PaymentService(0, 0).processPayment()).startsWith("TXN-");
    }

    @Test
    void processPayment_withFullFailureRate_shouldThrowPaymentException() {
        assertThatThrownBy(() -> new PaymentService(100, 0).processPayment())
                .isInstanceOf(PaymentException.class)
                .hasMessage("Payment Service unavailable");
    }
}
