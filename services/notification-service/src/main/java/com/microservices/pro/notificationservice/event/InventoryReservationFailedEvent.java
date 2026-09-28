package com.microservices.pro.notificationservice.event;

public record InventoryReservationFailedEvent(String orderId, String reason) {
}
