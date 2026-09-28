package com.microservices.pro.inventoryservice.event;

public record InventoryReservedEvent(String orderId, String productId, int quantity) {
}
