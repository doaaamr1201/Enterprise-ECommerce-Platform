package com.microservices.pro.orderservice.event;

public record PaymentCompletedEvent(String orderId, String transactionId) {
}
