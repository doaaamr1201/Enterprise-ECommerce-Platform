package com.microservices.pro.inventoryservice.model;

public record StockCheckResponse(
        String productId,
        int requestedQuantity,
        boolean available,
        int remainingStock
) {
}