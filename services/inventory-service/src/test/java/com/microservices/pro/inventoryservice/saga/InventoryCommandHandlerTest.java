package com.microservices.pro.inventoryservice.saga;

import com.microservices.pro.inventoryservice.event.InventoryReleasedEvent;
import com.microservices.pro.inventoryservice.event.InventoryResultEvent;
import com.microservices.pro.inventoryservice.event.ReleaseInventoryCommand;
import com.microservices.pro.inventoryservice.event.ReserveInventoryCommand;
import com.microservices.pro.inventoryservice.event.SagaTopics;
import com.microservices.pro.inventoryservice.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InventoryCommandHandlerTest {

    private InventoryService inventoryService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private InventoryCommandHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inventoryService = new InventoryService();
        kafkaTemplate = mock(KafkaTemplate.class);
        handler = new InventoryCommandHandler(inventoryService, kafkaTemplate);
    }

    @Test
    void reserveCommand_withStock_shouldReportSuccess() {
        handler.handleCommand(record(new ReserveInventoryCommand("ORD-1", "PROD-002", 2)));

        verify(kafkaTemplate).send(SagaTopics.SAGA_RESULTS, "ORD-1", new InventoryResultEvent("ORD-1", true, null));
        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(3);
    }

    @Test
    void reserveCommand_withoutStock_shouldReportFailure() {
        handler.handleCommand(record(new ReserveInventoryCommand("ORD-1", "PROD-003", 1)));

        verify(kafkaTemplate).send(SagaTopics.SAGA_RESULTS, "ORD-1",
                new InventoryResultEvent("ORD-1", false, "Insufficient stock for PROD-003"));
    }

    @Test
    void releaseCommand_shouldRestoreStockAndReportReleased() {
        handler.handleCommand(record(new ReserveInventoryCommand("ORD-1", "PROD-002", 2)));

        handler.handleCommand(record(new ReleaseInventoryCommand("ORD-1")));

        verify(kafkaTemplate).send(SagaTopics.SAGA_RESULTS, "ORD-1", new InventoryReleasedEvent("ORD-1"));
        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(5);
    }

    @Test
    void commandsForOtherServices_shouldBeIgnored() {
        handler.handleCommand(record(Map.of("orderId", "ORD-1", "amount", 10)));

        verifyNoInteractions(kafkaTemplate);
    }

    private static ConsumerRecord<String, Object> record(Object command) {
        return new ConsumerRecord<>(SagaTopics.SAGA_COMMANDS, 0, 0, "ORD-1", command);
    }
}
