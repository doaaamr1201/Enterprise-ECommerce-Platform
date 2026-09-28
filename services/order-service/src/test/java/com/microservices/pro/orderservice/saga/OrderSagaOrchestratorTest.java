package com.microservices.pro.orderservice.saga;

import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.event.InventoryReleasedEvent;
import com.microservices.pro.orderservice.event.InventoryResultEvent;
import com.microservices.pro.orderservice.event.PaymentResultEvent;
import com.microservices.pro.orderservice.event.ProcessPaymentCommand;
import com.microservices.pro.orderservice.event.ReleaseInventoryCommand;
import com.microservices.pro.orderservice.event.ReserveInventoryCommand;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderSagaOrchestratorTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private OrderRepository orderRepository;
    private OrderSagaOrchestrator orchestrator;
    private String orderId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void startSaga() {
        kafkaTemplate = mock(KafkaTemplate.class);
        orderRepository = new OrderRepository();
        orchestrator = new OrderSagaOrchestrator(kafkaTemplate, orderRepository);
        orderId = orchestrator.startSaga(new OrderRequest("PROD-001", 3, 250.0), "user123").orderId();
    }

    @Test
    void sagaState_shouldDefineAllNineStates() {
        assertThat(SagaState.values()).hasSize(9);
    }

    @Test
    void startSaga_shouldSavePendingOrderAndSendReserveInventory() {
        assertThat(orchestrator.getSagaState(orderId)).isEqualTo(SagaState.INVENTORY_RESERVING);
        assertThat(status()).isEqualTo(OrderStatus.PENDING);
        verify(kafkaTemplate).send(SagaTopics.SAGA_COMMANDS, orderId, new ReserveInventoryCommand(orderId, "PROD-001", 3));
    }

    @Test
    void inventoryReserved_shouldSendProcessPaymentWithOrderAmount() {
        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));

        assertThat(orchestrator.getSagaState(orderId)).isEqualTo(SagaState.PAYMENT_PROCESSING);
        verify(kafkaTemplate).send(SagaTopics.SAGA_COMMANDS, orderId, new ProcessPaymentCommand(orderId, BigDecimal.valueOf(250.0)));
    }

    @Test
    void inventoryReserveFailed_shouldCancelOrderWithoutCompensation() {
        clearInvocations(kafkaTemplate);

        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, false, "Insufficient stock")));

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orchestrator.getSagaState(orderId)).isNull();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void paymentCompleted_shouldConfirmOrder() {
        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));

        orchestrator.handlePaymentResult(result(new PaymentResultEvent(orderId, true, "TXN-1", null)));

        assertThat(status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orchestrator.getSagaState(orderId)).isNull();
    }

    @Test
    void paymentFailed_shouldSendReleaseInventory() {
        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));

        orchestrator.handlePaymentResult(result(new PaymentResultEvent(orderId, false, null, "card declined")));

        assertThat(orchestrator.getSagaState(orderId)).isEqualTo(SagaState.INVENTORY_RELEASING);
        verify(kafkaTemplate).send(SagaTopics.SAGA_COMMANDS, orderId, new ReleaseInventoryCommand(orderId));
    }

    @Test
    void inventoryReleased_shouldCancelOrder() {
        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));
        orchestrator.handlePaymentResult(result(new PaymentResultEvent(orderId, false, null, "card declined")));

        orchestrator.handleInventoryReleased(result(new InventoryReleasedEvent(orderId)));

        assertThat(status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orchestrator.getSagaState(orderId)).isNull();
    }

    @Test
    void duplicateInventoryResult_shouldBeIgnored() {
        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));
        clearInvocations(kafkaTemplate);

        orchestrator.handleInventoryResult(result(new InventoryResultEvent(orderId, true, null)));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertThat(orchestrator.getSagaState(orderId)).isEqualTo(SagaState.PAYMENT_PROCESSING);
    }

    private ConsumerRecord<String, Object> result(Object event) {
        return new ConsumerRecord<>(SagaTopics.SAGA_RESULTS, 0, 0, orderId, event);
    }

    private OrderStatus status() {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }
}
