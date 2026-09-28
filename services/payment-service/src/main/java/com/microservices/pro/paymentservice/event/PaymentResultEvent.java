package com.microservices.pro.paymentservice.event;

public record PaymentResultEvent(String orderId, boolean success, String transactionId, String reason) {
}
