package com.microservices.pro.orderservice.event;

import java.math.BigDecimal;

public record ProcessPaymentCommand(String orderId, BigDecimal amount) {
}
