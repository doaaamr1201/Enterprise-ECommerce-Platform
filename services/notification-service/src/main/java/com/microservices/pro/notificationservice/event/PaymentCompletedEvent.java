package com.microservices.pro.notificationservice.event;

public record PaymentCompletedEvent(String orderId, String transactionId) {
}
