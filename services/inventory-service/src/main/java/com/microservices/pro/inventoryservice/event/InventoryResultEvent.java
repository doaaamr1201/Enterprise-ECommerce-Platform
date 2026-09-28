package com.microservices.pro.inventoryservice.event;

public record InventoryResultEvent(String orderId, boolean success, String reason) {
}
