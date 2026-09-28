package com.microservices.pro.paymentservice.event;

public record PaymentFailedEvent(String orderId, String reason) {
}
