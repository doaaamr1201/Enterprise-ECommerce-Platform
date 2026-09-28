package com.microservices.pro.orderservice.event;

public record InventoryResultEvent(String orderId, boolean success, String reason) {
}
