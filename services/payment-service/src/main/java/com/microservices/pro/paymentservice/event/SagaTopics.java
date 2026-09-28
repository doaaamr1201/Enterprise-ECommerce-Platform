package com.microservices.pro.paymentservice.event;

public final class SagaTopics {

    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";

    private SagaTopics() {
    }
}
