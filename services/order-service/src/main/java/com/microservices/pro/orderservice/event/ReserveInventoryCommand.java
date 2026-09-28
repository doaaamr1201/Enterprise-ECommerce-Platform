package com.microservices.pro.orderservice.event;

public record ReserveInventoryCommand(String orderId, String productId, int quantity) {
}
