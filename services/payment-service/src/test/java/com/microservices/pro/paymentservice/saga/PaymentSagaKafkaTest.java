package com.microservices.pro.paymentservice.saga;

import com.microservices.pro.paymentservice.event.SagaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "eureka.client.enabled=false",
        "payment.failure-rate=0"
})
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.INVENTORY_EVENTS, SagaTopics.PAYMENT_EVENTS,
        SagaTopics.SAGA_COMMANDS, SagaTopics.SAGA_RESULTS})
class PaymentSagaKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> rawProducer;
    private Consumer<String, String> paymentEvents;
    private Consumer<String, String> sagaResults;

    @BeforeEach
    void setUp() {
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        paymentEvents = latestReader();
        broker.consumeFromAnEmbeddedTopic(paymentEvents, SagaTopics.PAYMENT_EVENTS);
        sagaResults = latestReader();
        broker.consumeFromAnEmbeddedTopic(sagaResults, SagaTopics.SAGA_RESULTS);
        listenerRegistry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void tearDown() {
        paymentEvents.close();
        sagaResults.close();
    }

    @Test
    void inventoryReservedFromInventoryService_shouldPublishPaymentCompleted() {
        send(SagaTopics.INVENTORY_EVENTS, "ORD-K1", "inventoryReserved",
                "{\"orderId\":\"ORD-K1\",\"productId\":\"PROD-001\",\"quantity\":3}");

        ConsumerRecord<String, String> completed = awaitPaymentEvent("ORD-K1", "paymentCompleted");

        assertThat(completed.value()).contains("\"transactionId\":\"TXN-");
    }

    @Test
    void processPaymentCommandAfterAnInventoryCommand_shouldIgnoreTheInventoryCommandAndReportPaymentResult() {
        send(SagaTopics.SAGA_COMMANDS, "ORD-K2", "reserveInventoryCommand",
                "{\"orderId\":\"ORD-K2\",\"productId\":\"PROD-001\",\"quantity\":1}");
        send(SagaTopics.SAGA_COMMANDS, "ORD-K2", "processPaymentCommand", "{\"orderId\":\"ORD-K2\",\"amount\":10.0}");

        ConsumerRecord<String, String> result = awaitRecord(sagaResults, "ORD-K2", "paymentResult");

        assertThat(result.value()).contains("\"success\":true", "\"transactionId\":\"TXN-");
    }

    private Consumer<String, String> latestReader() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(broker, "reader-" + UUID.randomUUID(), false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
    }

    private void send(String topic, String key, String typeId, String json) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
        rawProducer.send(record).join();
    }

    private ConsumerRecord<String, String> awaitPaymentEvent(String orderId, String expectedTypeId) {
        return awaitRecord(paymentEvents, orderId, expectedTypeId);
    }

    private ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer, String orderId, String expectedTypeId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (orderId.equals(record.key()) && expectedTypeId.equals(typeId(record))) {
                    return record;
                }
            }
        }
        throw new AssertionError("No " + expectedTypeId + " event for " + orderId);
    }

    private static String typeId(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8);
    }
}
