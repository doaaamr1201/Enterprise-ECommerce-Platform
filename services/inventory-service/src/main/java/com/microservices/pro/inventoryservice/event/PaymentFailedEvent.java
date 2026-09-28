package com.microservices.pro.inventoryservice.event;

public record PaymentFailedEvent(String orderId, String reason) {
}
