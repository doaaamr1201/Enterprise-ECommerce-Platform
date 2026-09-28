package com.microservices.pro.inventoryservice.saga;

import com.microservices.pro.inventoryservice.event.InventoryReleasedEvent;
import com.microservices.pro.inventoryservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.inventoryservice.event.InventoryReservedEvent;
import com.microservices.pro.inventoryservice.event.OrderPlacedEvent;
import com.microservices.pro.inventoryservice.event.PaymentCompletedEvent;
import com.microservices.pro.inventoryservice.event.PaymentFailedEvent;
import com.microservices.pro.inventoryservice.event.SagaTopics;
import com.microservices.pro.inventoryservice.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InventorySagaHandlerTest {

    private InventoryService inventoryService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private InventorySagaHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inventoryService = new InventoryService();
        kafkaTemplate = mock(KafkaTemplate.class);
        handler = new InventorySagaHandler(inventoryService, kafkaTemplate);
    }

    @Test
    void orderPlaced_withStock_shouldPublishInventoryReserved() {
        handler.handleOrderPlaced(orderPlaced("ORD-1", "PROD-001", 3));

        verify(kafkaTemplate).send(SagaTopics.INVENTORY_EVENTS, "ORD-1", new InventoryReservedEvent("ORD-1", "PROD-001", 3));
    }

    @Test
    void orderPlaced_withoutStock_shouldPublishReservationFailed() {
        handler.handleOrderPlaced(orderPlaced("ORD-2", "PROD-003", 1));

        verify(kafkaTemplate).send(SagaTopics.INVENTORY_EVENTS, "ORD-2",
                new InventoryReservationFailedEvent("ORD-2", "Insufficient stock for PROD-003"));
    }

    @Test
    void paymentFailed_shouldReleaseStockAndPublishInventoryReleased() {
        handler.handleOrderPlaced(orderPlaced("ORD-1", "PROD-002", 5));

        handler.handlePaymentEvent(record(new PaymentFailedEvent("ORD-1", "card declined")));

        verify(kafkaTemplate).send(SagaTopics.INVENTORY_EVENTS, "ORD-1", new InventoryReleasedEvent("ORD-1"));
        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(5);
    }

    @Test
    void paymentCompleted_shouldNotReleaseAnything() {
        handler.handleOrderPlaced(orderPlaced("ORD-1", "PROD-001", 3));

        handler.handlePaymentEvent(record(new PaymentCompletedEvent("ORD-1", "TXN-1")));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(InventoryReleasedEvent.class));
    }

    private static ConsumerRecord<String, Object> record(Object event) {
        return new ConsumerRecord<>(SagaTopics.PAYMENT_EVENTS, 0, 0, "ORD-1", event);
    }

    private static OrderPlacedEvent orderPlaced(String orderId, String productId, int quantity) {
        return new OrderPlacedEvent(orderId, productId, quantity, BigDecimal.TEN, "user123");
    }
}
