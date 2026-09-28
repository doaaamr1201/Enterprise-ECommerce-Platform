package com.microservices.pro.orderservice.event;

public record InventoryReservedEvent(String orderId, String productId, int quantity) {
}
