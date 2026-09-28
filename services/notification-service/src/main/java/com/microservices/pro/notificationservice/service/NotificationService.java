package com.microservices.pro.notificationservice.service;

import com.microservices.pro.notificationservice.event.InventoryReleasedEvent;
import com.microservices.pro.notificationservice.event.InventoryReservationFailedEvent;
import com.microservices.pro.notificationservice.event.PaymentCompletedEvent;
import com.microservices.pro.notificationservice.event.SagaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = SagaTopics.PAYMENT_EVENTS, groupId = "notification-service")
    public void handlePaymentCompleted(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof PaymentCompletedEvent event)) {
            return;
        }
        requireOrderId(event.orderId());
        log.info("[NOTIFICATION] Sending confirmation email for order: {}", event.orderId());
        sendConfirmationEmail(event.orderId(), event.transactionId());
        log.info("[NOTIFICATION] Confirmation sent for order: {}", event.orderId());
    }

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = SagaTopics.INVENTORY_EVENTS, groupId = "notification-service-cancel")
    public void handleOrderCancelled(ConsumerRecord<String, Object> record) {
        if (record.value() instanceof InventoryReleasedEvent event) {
            requireOrderId(event.orderId());
            sendCancellationNotification(event.orderId(), "Payment failed");
        } else if (record.value() instanceof InventoryReservationFailedEvent event) {
            requireOrderId(event.orderId());
            sendCancellationNotification(event.orderId(), event.reason());
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, Object> record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("[NOTIFICATION] DLT: Event from topic '{}' exhausted all retries. Manual intervention required. Event: {}",
                topic, record.value());
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Event has no orderId");
        }
    }

    private void sendConfirmationEmail(String orderId, String transactionId) {
        log.info("[EMAIL] Order {} confirmed. Transaction: {}", orderId, transactionId);
    }

    private void sendCancellationNotification(String orderId, String reason) {
        log.info("[NOTIFICATION] Sending cancellation notification for order: {}", orderId);
        log.info("[EMAIL] Order {} cancelled. Reason: {}", orderId, reason);
    }
}
