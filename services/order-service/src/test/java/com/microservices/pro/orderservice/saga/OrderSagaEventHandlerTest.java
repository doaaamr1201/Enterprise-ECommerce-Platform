package com.microservices.pro.orderservice.saga;

import com.microservices.pro.orderservice.event.InventoryReleasedEvent;
import com.microservices.pro.orderservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.orderservice.event.InventoryReservedEvent;
import com.microservices.pro.orderservice.event.PaymentCompletedEvent;
import com.microservices.pro.orderservice.event.PaymentFailedEvent;
import com.microservices.pro.orderservice.model.Order;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSagaEventHandlerTest {

    private static final String ORDER_ID = "ORD-1";

    private OrderRepository orderRepository;
    private OrderSagaEventHandler handler;

    @BeforeEach
    void setUp() {
        orderRepository = new OrderRepository();
        orderRepository.save(new Order(ORDER_ID, "PROD-001", 3, BigDecimal.TEN, "user123", OrderStatus.PENDING));
        handler = new OrderSagaEventHandler(orderRepository);
    }

    @Test
    void paymentCompleted_shouldConfirmOrder() {
        handler.handlePaymentEvent(record(new PaymentCompletedEvent(ORDER_ID, "TXN-1")));

        assertThat(status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentFailed_shouldMarkOrderPaymentFailed() {
        handler.handlePaymentEvent(record(new PaymentFailedEvent(ORDER_ID, "card declined")));

        assertThat(status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void inventoryReleased_shouldCancelOrder() {
        handler.handlePaymentEvent(record(new PaymentFailedEvent(ORDER_ID, "card declined")));

        handler.handleInventoryEvent(record(new InventoryReleasedEvent(ORDER_ID)));

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void inventoryReservationFailed_shouldCancelOrder() {
        handler.handleInventoryEvent(record(new InventoryReservationFailedEvent(ORDER_ID, "Insufficient stock")));

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void inventoryReserved_shouldLeaveOrderPending() {
        handler.handleInventoryEvent(record(new InventoryReservedEvent(ORDER_ID, "PROD-001", 3)));

        assertThat(status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cancelledOrder_shouldIgnoreLateEvents() {
        handler.handleInventoryEvent(record(new InventoryReleasedEvent(ORDER_ID)));

        handler.handlePaymentEvent(record(new PaymentCompletedEvent(ORDER_ID, "TXN-1")));

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
    }

    private static ConsumerRecord<String, Object> record(Object event) {
        return new ConsumerRecord<>("saga-events", 0, 0, ORDER_ID, event);
    }

    private OrderStatus status() {
        return orderRepository.findById(ORDER_ID).orElseThrow().getStatus();
    }
}
