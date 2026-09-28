package com.microservices.pro.inventoryservice.saga;

import com.microservices.pro.inventoryservice.event.InventoryReleasedEvent;
import com.microservices.pro.inventoryservice.event.InventoryResultEvent;
import com.microservices.pro.inventoryservice.event.ReleaseInventoryCommand;
import com.microservices.pro.inventoryservice.event.ReserveInventoryCommand;
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
public class InventoryCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandHandler.class);

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryCommandHandler(InventoryService inventoryService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.inventoryService = inventoryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.SAGA_COMMANDS, groupId = "inventory-orchestration")
    public void handleCommand(ConsumerRecord<String, Object> record) {
        if (record.value() instanceof ReserveInventoryCommand command) {
            reserve(command);
        } else if (record.value() instanceof ReleaseInventoryCommand command) {
            release(command);
        }
    }

    private void reserve(ReserveInventoryCommand command) {
        try {
            inventoryService.reserveStock(command.productId(), command.quantity(), command.orderId());
            kafkaTemplate.send(SagaTopics.SAGA_RESULTS, command.orderId(),
                    new InventoryResultEvent(command.orderId(), true, null));
            log.info("[SAGA] Inventory reserved for order: {}", command.orderId());
        } catch (InsufficientStockException e) {
            kafkaTemplate.send(SagaTopics.SAGA_RESULTS, command.orderId(),
                    new InventoryResultEvent(command.orderId(), false, e.getMessage()));
            log.warn("[SAGA] Inventory reservation FAILED for order: {}", command.orderId());
        }
    }

    private void release(ReleaseInventoryCommand command) {
        boolean released = inventoryService.releaseStock(command.orderId());
        kafkaTemplate.send(SagaTopics.SAGA_RESULTS, command.orderId(), new InventoryReleasedEvent(command.orderId()));
        log.info("[SAGA] COMPENSATION: Inventory release for order {} (reservation found: {})", command.orderId(), released);
    }
}
