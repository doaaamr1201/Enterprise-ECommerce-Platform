package com.microservices.pro.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class Order {

    private final String orderId;
    private final String productId;
    private final int quantity;
    private final BigDecimal amount;
    private final String customerId;

    @Setter
    private OrderStatus status;
}
