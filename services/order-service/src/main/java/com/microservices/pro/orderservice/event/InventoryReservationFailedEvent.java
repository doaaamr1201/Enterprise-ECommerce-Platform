package com.microservices.pro.orderservice.event;

public record InventoryReservationFailedEvent(String orderId, String reason) {
}
