package com.microservices.pro.paymentservice.event;

public record InventoryReservationFailedEvent(String orderId, String reason) {
}
