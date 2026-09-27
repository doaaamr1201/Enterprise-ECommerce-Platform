package com.microservices.pro.orderservice.dto;

public record OrderRequest(
        String productId,
        int quantity,
        Double amount
) {
}