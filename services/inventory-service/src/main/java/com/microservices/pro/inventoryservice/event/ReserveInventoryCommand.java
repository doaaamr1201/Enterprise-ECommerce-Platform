package com.microservices.pro.inventoryservice.event;

public record ReserveInventoryCommand(String orderId, String productId, int quantity) {
}
