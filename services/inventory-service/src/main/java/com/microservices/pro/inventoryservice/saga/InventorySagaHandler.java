package com.microservices.pro.inventoryservice.saga;

import com.microservices.pro.inventoryservice.event.InventoryReleasedEvent;
import com.microservices.pro.inventoryservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.inventoryservice.event.InventoryReservedEvent;
import com.microservices.pro.inventoryservice.event.OrderPlacedEvent;
import com.microservices.pro.inventoryservice.event.PaymentFailedEvent;
import com.microservices.pro.inventoryservice.event.SagaTopics;
import com.microservices.pro.inventoryservice.exception.InsufficientStockException;
import com.microservices.pro.inventoryservice.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventorySagaHandler {

    private static final Logger log = LoggerFactory.getLogger(InventorySagaHandler.class);

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventorySagaHandler(InventoryService inventoryService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.inventoryService = inventoryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.ORDER_EVENTS, groupId = "inventory-service")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("[SAGA] Handling OrderPlaced for order: {}", event.orderId());
        try {
            inventoryService.reserveStock(event.productId(), event.quantity(), event.orderId());
            kafkaTemplate.send(SagaTopics.INVENTORY_EVENTS, event.orderId(),
                    new InventoryReservedEvent(event.orderId(), event.productId(), event.quantity()));
            log.info("[SAGA] Inventory reserved for order: {}", event.orderId());
        } catch (InsufficientStockException e) {
            kafkaTemplate.send(SagaTopics.INVENTORY_EVENTS, event.orderId(),
                    new InventoryReservationFailedEvent(event.orderId(), e.getMessage()));
            log.warn("[SAGA] Inventory reservation FAILED for order: {}", event.orderId());
        }
    }

    @KafkaListener(topics = SagaTopics.PAYMENT_EVENTS, groupId = "inventory-compensation")
    public void handlePaymentEvent(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof PaymentFailedEvent failed)) {
            return;
        }
        if (inventoryService.releaseStock(failed.orderId())) {
            kafkaTemplate.send(SagaTopics.INVENTORY_EVENTS, failed.orderId(), new InventoryReleasedEvent(failed.orderId()));
            log.info("[SAGA] COMPENSATION: Inventory released for order: {}", failed.orderId());
        } else {
            log.warn("[SAGA] COMPENSATION skipped -- no reservation for order: {}", failed.orderId());
        }
    }
}
