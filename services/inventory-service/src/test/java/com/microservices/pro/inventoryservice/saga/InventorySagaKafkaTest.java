package com.microservices.pro.inventoryservice.saga;

import com.microservices.pro.inventoryservice.event.SagaTopics;
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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.ORDER_EVENTS, SagaTopics.INVENTORY_EVENTS, SagaTopics.PAYMENT_EVENTS,
        SagaTopics.SAGA_COMMANDS, SagaTopics.SAGA_RESULTS})
class InventorySagaKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> rawProducer;
    private Consumer<String, String> inventoryEvents;
    private Consumer<String, String> sagaResults;

    @BeforeEach
    void setUp() {
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        inventoryEvents = latestReader();
        broker.consumeFromAnEmbeddedTopic(inventoryEvents, SagaTopics.INVENTORY_EVENTS);
        sagaResults = latestReader();
        broker.consumeFromAnEmbeddedTopic(sagaResults, SagaTopics.SAGA_RESULTS);
        listenerRegistry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void tearDown() {
        inventoryEvents.close();
        sagaResults.close();
    }

    @Test
    void orderPlacedFromOrderService_shouldReserveStockAndPublishInventoryReserved() {
        send(SagaTopics.ORDER_EVENTS, "ORD-K1", "orderPlaced",
                "{\"orderId\":\"ORD-K1\",\"productId\":\"PROD-001\",\"quantity\":3,\"amount\":250.0,\"customerId\":\"user123\"}");

        ConsumerRecord<String, String> reserved = awaitInventoryEvent("ORD-K1", "inventoryReserved");

        assertThat(reserved.value()).contains("\"productId\":\"PROD-001\"", "\"quantity\":3");
    }

    @Test
    void paymentFailedFromPaymentService_shouldReleaseStockAndPublishInventoryReleased() {
        send(SagaTopics.ORDER_EVENTS, "ORD-K2", "orderPlaced",
                "{\"orderId\":\"ORD-K2\",\"productId\":\"PROD-002\",\"quantity\":2,\"amount\":20.0,\"customerId\":\"user123\"}");
        awaitInventoryEvent("ORD-K2", "inventoryReserved");

        send(SagaTopics.PAYMENT_EVENTS, "ORD-K2", "paymentFailed",
                "{\"orderId\":\"ORD-K2\",\"reason\":\"Payment Service unavailable\"}");

        ConsumerRecord<String, String> released = awaitInventoryEvent("ORD-K2", "inventoryReleased");

        assertThat(released.value()).contains("\"orderId\":\"ORD-K2\"");
    }

    @Test
    void reserveCommandAfterAPaymentCommand_shouldIgnoreThePaymentCommandAndReportInventoryResult() {
        send(SagaTopics.SAGA_COMMANDS, "ORD-K3", "processPaymentCommand", "{\"orderId\":\"ORD-K3\",\"amount\":10.0}");
        send(SagaTopics.SAGA_COMMANDS, "ORD-K3", "reserveInventoryCommand",
                "{\"orderId\":\"ORD-K3\",\"productId\":\"PROD-001\",\"quantity\":1}");

        ConsumerRecord<String, String> result = awaitRecord(sagaResults, "ORD-K3", "inventoryResult");

        assertThat(result.value()).contains("\"success\":true");
    }

    @Test
    void releaseCommand_shouldReportInventoryReleased() {
        send(SagaTopics.SAGA_COMMANDS, "ORD-K4", "reserveInventoryCommand",
                "{\"orderId\":\"ORD-K4\",\"productId\":\"PROD-001\",\"quantity\":1}");
        awaitRecord(sagaResults, "ORD-K4", "inventoryResult");

        send(SagaTopics.SAGA_COMMANDS, "ORD-K4", "releaseInventoryCommand", "{\"orderId\":\"ORD-K4\"}");

        awaitRecord(sagaResults, "ORD-K4", "inventoryReleased");
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

    private ConsumerRecord<String, String> awaitInventoryEvent(String orderId, String expectedTypeId) {
        return awaitRecord(inventoryEvents, orderId, expectedTypeId);
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
