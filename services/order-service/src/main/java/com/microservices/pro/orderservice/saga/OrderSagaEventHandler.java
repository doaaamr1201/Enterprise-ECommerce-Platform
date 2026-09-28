package com.microservices.pro.orderservice.saga;

import com.microservices.pro.orderservice.event.InventoryReleasedEvent;
import com.microservices.pro.orderservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.orderservice.event.PaymentCompletedEvent;
import com.microservices.pro.orderservice.event.PaymentFailedEvent;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaEventHandler {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = SagaTopics.PAYMENT_EVENTS, groupId = "order-service")
    public void handlePaymentEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event instanceof PaymentCompletedEvent completed) {
            updateStatus(completed.orderId(), OrderStatus.CONFIRMED);
            log.info("[SAGA] Order {} CONFIRMED", completed.orderId());
        } else if (event instanceof PaymentFailedEvent failed) {
            updateStatus(failed.orderId(), OrderStatus.PAYMENT_FAILED);
            log.warn("[SAGA] Order {} payment failed, waiting for inventory release...", failed.orderId());
        }
    }

    @KafkaListener(topics = SagaTopics.INVENTORY_EVENTS, groupId = "order-service-cancel")
    public void handleInventoryEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event instanceof InventoryReleasedEvent released) {
            updateStatus(released.orderId(), OrderStatus.CANCELLED);
            log.info("[SAGA] Order {} CANCELLED -- inventory released", released.orderId());
        } else if (event instanceof InventoryReservationFailedEvent failed) {
            updateStatus(failed.orderId(), OrderStatus.CANCELLED);
            log.warn("[SAGA] Order {} CANCELLED -- {}", failed.orderId(), failed.reason());
        }
    }

    private void updateStatus(String orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    if (order.getStatus() != OrderStatus.CANCELLED) {
                        order.setStatus(status);
                        orderRepository.save(order);
                    }
                },
                () -> log.warn("[SAGA] Unknown order {}", orderId));
    }
}
