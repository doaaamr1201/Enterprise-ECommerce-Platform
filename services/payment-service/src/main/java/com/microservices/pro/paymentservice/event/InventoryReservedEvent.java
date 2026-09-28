package com.microservices.pro.paymentservice.event;

public record InventoryReservedEvent(String orderId, String productId, int quantity) {
}
