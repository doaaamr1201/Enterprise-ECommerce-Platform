package com.microservices.pro.paymentservice.saga;

import com.microservices.pro.paymentservice.event.PaymentResultEvent;
import com.microservices.pro.paymentservice.event.ProcessPaymentCommand;
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
public class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentCommandHandler(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = SagaTopics.SAGA_COMMANDS, groupId = "payment-orchestration")
    public void handleCommand(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof ProcessPaymentCommand command)) {
            return;
        }
        try {
            String transactionId = paymentService.processPayment();
            kafkaTemplate.send(SagaTopics.SAGA_RESULTS, command.orderId(),
                    new PaymentResultEvent(command.orderId(), true, transactionId, null));
            log.info("[SAGA] Payment COMPLETED for order: {}", command.orderId());
        } catch (PaymentException e) {
            kafkaTemplate.send(SagaTopics.SAGA_RESULTS, command.orderId(),
                    new PaymentResultEvent(command.orderId(), false, null, e.getMessage()));
            log.warn("[SAGA] Payment FAILED for order: {}", command.orderId());
        }
    }
}
