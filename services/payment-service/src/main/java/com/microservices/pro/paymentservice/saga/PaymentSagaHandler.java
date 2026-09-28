package com.microservices.pro.paymentservice.saga;

import com.microservices.pro.paymentservice.event.InventoryReservedEvent;
import com.microservices.pro.paymentservice.event.PaymentCompletedEvent;
import com.microservices.pro.paymentservice.event.PaymentFailedEvent;
import com.microservices.pro.paymentservice.event.SagaTopics;
import com.microservices.pro.paymentservice.exception.PaymentException;
import com.microservices.pro.paymentservice.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaHandler.class);

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentSagaHandler(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.INVENTORY_EVENTS, groupId = "payment-service")
    public void handleInventoryReserved(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof InventoryReservedEvent event)) {
            return;
        }
        log.info("[SAGA] Processing payment for order: {}", event.orderId());
        try {
            String transactionId = paymentService.processPayment();
            kafkaTemplate.send(SagaTopics.PAYMENT_EVENTS, event.orderId(),
                    new PaymentCompletedEvent(event.orderId(), transactionId));
            log.info("[SAGA] Payment COMPLETED for order: {}", event.orderId());
        } catch (PaymentException e) {
            kafkaTemplate.send(SagaTopics.PAYMENT_EVENTS, event.orderId(),
                    new PaymentFailedEvent(event.orderId(), e.getMessage()));
            log.warn("[SAGA] Payment FAILED for order: {} -- triggering compensation", event.orderId());
        }
    }
}
