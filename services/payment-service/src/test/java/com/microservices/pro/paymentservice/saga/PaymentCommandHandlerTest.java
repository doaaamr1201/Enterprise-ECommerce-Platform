package com.microservices.pro.paymentservice.saga;

import com.microservices.pro.paymentservice.event.PaymentResultEvent;
import com.microservices.pro.paymentservice.event.ProcessPaymentCommand;
import com.microservices.pro.paymentservice.event.SagaTopics;
import com.microservices.pro.paymentservice.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentCommandHandlerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    @Test
    void processPayment_success_shouldReportTransaction() {
        PaymentCommandHandler handler = new PaymentCommandHandler(new PaymentService(0, 0), kafkaTemplate);
        ArgumentCaptor<Object> result = ArgumentCaptor.forClass(Object.class);

        handler.handleCommand(record(new ProcessPaymentCommand("ORD-1", BigDecimal.TEN)));

        verify(kafkaTemplate).send(eq(SagaTopics.SAGA_RESULTS), eq("ORD-1"), result.capture());
        assertThat(result.getValue()).isInstanceOfSatisfying(PaymentResultEvent.class, event -> {
            assertThat(event.success()).isTrue();
            assertThat(event.transactionId()).startsWith("TXN-");
        });
    }

    @Test
    void processPayment_failure_shouldReportFailure() {
        PaymentCommandHandler handler = new PaymentCommandHandler(new PaymentService(100, 0), kafkaTemplate);

        handler.handleCommand(record(new ProcessPaymentCommand("ORD-1", BigDecimal.TEN)));

        verify(kafkaTemplate).send(SagaTopics.SAGA_RESULTS, "ORD-1",
                new PaymentResultEvent("ORD-1", false, null, "Payment Service unavailable"));
    }

    @Test
    void commandsForOtherServices_shouldBeIgnored() {
        PaymentCommandHandler handler = new PaymentCommandHandler(new PaymentService(0, 0), kafkaTemplate);

        handler.handleCommand(record(Map.of("orderId", "ORD-1")));

        verifyNoInteractions(kafkaTemplate);
    }

    private static ConsumerRecord<String, Object> record(Object command) {
        return new ConsumerRecord<>(SagaTopics.SAGA_COMMANDS, 0, 0, "ORD-1", command);
    }
}
