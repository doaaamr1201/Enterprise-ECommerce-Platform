package com.microservices.pro.orderservice.event;

public record InventoryReleasedEvent(String orderId) {
}
