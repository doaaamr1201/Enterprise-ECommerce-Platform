package com.microservices.pro.orderservice.dto;

import com.microservices.pro.orderservice.model.OrderStatus;

public record OrderStatusResponse(String orderId, OrderStatus status) {
}
