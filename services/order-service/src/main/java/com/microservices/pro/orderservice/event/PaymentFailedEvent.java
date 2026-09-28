package com.microservices.pro.orderservice.event;

public record PaymentFailedEvent(String orderId, String reason) {
}
