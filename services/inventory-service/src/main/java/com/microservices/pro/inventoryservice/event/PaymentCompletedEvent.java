package com.microservices.pro.inventoryservice.event;

public record PaymentCompletedEvent(String orderId, String transactionId) {
}
