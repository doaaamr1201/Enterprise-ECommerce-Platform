package com.microservices.pro.notificationservice.service;

import com.microservices.pro.notificationservice.event.InventoryReleasedEvent;
import com.microservices.pro.notificationservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.notificationservice.event.PaymentCompletedEvent;
import com.microservices.pro.notificationservice.event.SagaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    @Test
    void paymentCompleted_shouldBeHandled() {
        assertThatCode(() -> notificationService.handlePaymentCompleted(
                record(SagaTopics.PAYMENT_EVENTS, new PaymentCompletedEvent("ORD-1", "TXN-1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void paymentCompletedWithoutOrderId_shouldFailSoItIsRetried() {
        assertThatThrownBy(() -> notificationService.handlePaymentCompleted(
                record(SagaTopics.PAYMENT_EVENTS, new PaymentCompletedEvent(null, "TXN-1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherPaymentEvents_shouldBeIgnored() {
        assertThatCode(() -> notificationService.handlePaymentCompleted(
                record(SagaTopics.PAYMENT_EVENTS, Map.of("orderId", "ORD-1", "reason", "declined"))))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelledOrders_shouldBeHandled() {
        assertThatCode(() -> {
            notificationService.handleOrderCancelled(record(SagaTopics.INVENTORY_EVENTS, new InventoryReleasedEvent("ORD-1")));
            notificationService.handleOrderCancelled(record(SagaTopics.INVENTORY_EVENTS,
                    new InventoryReservationFailedEvent("ORD-2", "Insufficient stock for PROD-003")));
        }).doesNotThrowAnyException();
    }

    private static ConsumerRecord<String, Object> record(String topic, Object value) {
        return new ConsumerRecord<>(topic, 0, 0, "ORD-1", value);
    }
}
