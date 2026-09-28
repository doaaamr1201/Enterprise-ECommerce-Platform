package com.microservices.pro.paymentservice.saga;

import com.microservices.pro.paymentservice.event.InventoryReleasedEvent;
import com.microservices.pro.paymentservice.event.InventoryReservedEvent;
import com.microservices.pro.paymentservice.event.PaymentCompletedEvent;
import com.microservices.pro.paymentservice.event.PaymentFailedEvent;
import com.microservices.pro.paymentservice.event.SagaTopics;
import com.microservices.pro.paymentservice.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentSagaHandlerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    @Test
    void inventoryReserved_andPaymentSucceeds_shouldPublishPaymentCompleted() {
        PaymentSagaHandler handler = new PaymentSagaHandler(new PaymentService(0, 0), kafkaTemplate);

        handler.handleInventoryReserved(record(new InventoryReservedEvent("ORD-1", "PROD-001", 3)));

        verify(kafkaTemplate).send(eq(SagaTopics.PAYMENT_EVENTS), eq("ORD-1"), any(PaymentCompletedEvent.class));
    }

    @Test
    void inventoryReserved_andPaymentFails_shouldPublishPaymentFailed() {
        PaymentSagaHandler handler = new PaymentSagaHandler(new PaymentService(100, 0), kafkaTemplate);

        handler.handleInventoryReserved(record(new InventoryReservedEvent("ORD-1", "PROD-001", 3)));

        verify(kafkaTemplate).send(SagaTopics.PAYMENT_EVENTS, "ORD-1",
                new PaymentFailedEvent("ORD-1", "Payment Service unavailable"));
    }

    @Test
    void otherInventoryEvents_shouldBeIgnored() {
        PaymentSagaHandler handler = new PaymentSagaHandler(new PaymentService(0, 0), kafkaTemplate);

        handler.handleInventoryReserved(record(new InventoryReleasedEvent("ORD-1")));

        verifyNoInteractions(kafkaTemplate);
    }

    private static ConsumerRecord<String, Object> record(Object event) {
        return new ConsumerRecord<>(SagaTopics.INVENTORY_EVENTS, 0, 0, "ORD-1", event);
    }
}
