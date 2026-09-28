package com.microservices.pro.orderservice.dto;

import com.microservices.pro.orderservice.model.OrderStatus;

public record SagaOrderResponse(String orderId, OrderStatus status, String message) {
}
