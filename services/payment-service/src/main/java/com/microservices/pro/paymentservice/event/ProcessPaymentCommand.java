package com.microservices.pro.paymentservice.event;

import java.math.BigDecimal;

public record ProcessPaymentCommand(String orderId, BigDecimal amount) {
}
