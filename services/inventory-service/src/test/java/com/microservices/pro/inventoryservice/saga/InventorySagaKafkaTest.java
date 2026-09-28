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
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.ORDER_EVENTS, SagaTopics.INVENTORY_EVENTS, SagaTopics.PAYMENT_EVENTS})
class InventorySagaKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> rawProducer;
    private Consumer<String, String> inventoryEvents;

    @BeforeEach
    void setUp() {
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(broker, "reader-" + UUID.randomUUID(), false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        inventoryEvents = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(inventoryEvents, SagaTopics.INVENTORY_EVENTS);
        listenerRegistry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void tearDown() {
        inventoryEvents.close();
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

    private void send(String topic, String key, String typeId, String json) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
        rawProducer.send(record).join();
    }

    private ConsumerRecord<String, String> awaitInventoryEvent(String orderId, String expectedTypeId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : inventoryEvents.poll(Duration.ofMillis(500))) {
                if (orderId.equals(record.key()) && expectedTypeId.equals(typeId(record))) {
                    return record;
                }
            }
        }
        throw new AssertionError("No " + expectedTypeId + " event for " + orderId + " on " + SagaTopics.INVENTORY_EVENTS);
    }

    private static String typeId(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8);
    }
}
