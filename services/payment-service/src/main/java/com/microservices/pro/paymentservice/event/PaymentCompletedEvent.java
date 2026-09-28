package com.microservices.pro.paymentservice.event;

public record PaymentCompletedEvent(String orderId, String transactionId) {
}
