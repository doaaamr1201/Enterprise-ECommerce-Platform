package com.microservices.pro.orderservice.event;

public record PaymentResultEvent(String orderId, boolean success, String transactionId, String reason) {
}
