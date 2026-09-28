package com.microservices.pro.inventoryservice.event;

public record InventoryReservationFailedEvent(String orderId, String reason) {
}
