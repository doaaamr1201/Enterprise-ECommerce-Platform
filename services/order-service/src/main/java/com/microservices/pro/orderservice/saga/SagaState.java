package com.microservices.pro.orderservice.saga;

public enum SagaState {
    STARTED,
    INVENTORY_RESERVING,
    INVENTORY_RESERVED,
    PAYMENT_PROCESSING,
    COMPLETED,
    INVENTORY_RESERVE_FAILED,
    PAYMENT_FAILED,
    INVENTORY_RELEASING,
    CANCELLED
}
