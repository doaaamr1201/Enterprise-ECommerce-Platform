package com.microservices.pro.orderservice.saga;

import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.event.InventoryReleasedEvent;
import com.microservices.pro.orderservice.event.InventoryResultEvent;
import com.microservices.pro.orderservice.event.PaymentResultEvent;
import com.microservices.pro.orderservice.event.ProcessPaymentCommand;
import com.microservices.pro.orderservice.event.ReleaseInventoryCommand;
import com.microservices.pro.orderservice.event.ReserveInventoryCommand;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.model.Order;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final Map<String, SagaState> sagaStates = new ConcurrentHashMap<>();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderRepository orderRepository;

    public SagaOrderResponse startSaga(OrderRequest request, String customerId) {
        String orderId = UUID.randomUUID().toString();
        orderRepository.save(new Order(
                orderId,
                request.productId(),
                request.quantity(),
                request.amount() == null ? null : BigDecimal.valueOf(request.amount()),
                customerId,
                OrderStatus.PENDING
        ));

        transition(orderId, SagaState.STARTED);
        kafkaTemplate.send(SagaTopics.SAGA_COMMANDS, orderId,
                new ReserveInventoryCommand(orderId, request.productId(), request.quantity()));
        transition(orderId, SagaState.INVENTORY_RESERVING);

        return new SagaOrderResponse(orderId, OrderStatus.PENDING, "Order received -- processing...");
    }

    @KafkaListener(topics = SagaTopics.SAGA_RESULTS, groupId = "orchestrator-inventory")
    public void handleInventoryResult(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof InventoryResultEvent event)) {
            return;
        }
        SagaState current = sagaStates.get(event.orderId());
        if (current != SagaState.INVENTORY_RESERVING) {
            log.warn("[SAGA] Unexpected state {} for order {}", current, event.orderId());
            return;
        }

        if (event.success()) {
            transition(event.orderId(), SagaState.INVENTORY_RESERVED);
            kafkaTemplate.send(SagaTopics.SAGA_COMMANDS, event.orderId(),
                    new ProcessPaymentCommand(event.orderId(), getOrderAmount(event.orderId())));
            transition(event.orderId(), SagaState.PAYMENT_PROCESSING);
        } else {
            transition(event.orderId(), SagaState.INVENTORY_RESERVE_FAILED);
            updateOrderStatus(event.orderId(), OrderStatus.CANCELLED);
            sagaStates.remove(event.orderId());
            log.info("[SAGA] Order {} CANCELLED -- {}", event.orderId(), event.reason());
        }
    }

    @KafkaListener(topics = SagaTopics.SAGA_RESULTS, groupId = "orchestrator-payment")
    public void handlePaymentResult(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof PaymentResultEvent event)) {
            return;
        }
        if (sagaStates.get(event.orderId()) != SagaState.PAYMENT_PROCESSING) {
            return;
        }

        if (event.success()) {
            transition(event.orderId(), SagaState.COMPLETED);
            updateOrderStatus(event.orderId(), OrderStatus.CONFIRMED);
            sagaStates.remove(event.orderId());
            log.info("[SAGA] Order {} CONFIRMED", event.orderId());
        } else {
            transition(event.orderId(), SagaState.PAYMENT_FAILED);
            kafkaTemplate.send(SagaTopics.SAGA_COMMANDS, event.orderId(), new ReleaseInventoryCommand(event.orderId()));
            transition(event.orderId(), SagaState.INVENTORY_RELEASING);
        }
    }

    @KafkaListener(topics = SagaTopics.SAGA_RESULTS, groupId = "orchestrator-compensation")
    public void handleInventoryReleased(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof InventoryReleasedEvent event)) {
            return;
        }
        if (sagaStates.get(event.orderId()) != SagaState.INVENTORY_RELEASING) {
            return;
        }

        transition(event.orderId(), SagaState.CANCELLED);
        updateOrderStatus(event.orderId(), OrderStatus.CANCELLED);
        sagaStates.remove(event.orderId());
        log.info("[SAGA] Order {} CANCELLED -- compensation complete", event.orderId());
    }

    SagaState getSagaState(String orderId) {
        return sagaStates.get(orderId);
    }

    private BigDecimal getOrderAmount(String orderId) {
        return orderRepository.findById(orderId).map(Order::getAmount).orElse(null);
    }

    private void updateOrderStatus(String orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
        });
    }

    private void transition(String orderId, SagaState newState) {
        SagaState old = sagaStates.put(orderId, newState);
        log.info("[SAGA] {} {} -> {}", orderId, old, newState);
    }
}
