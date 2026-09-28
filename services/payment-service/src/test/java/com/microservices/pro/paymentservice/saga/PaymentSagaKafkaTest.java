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
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.INVENTORY_EVENTS, SagaTopics.PAYMENT_EVENTS})
class PaymentSagaKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> rawProducer;
    private Consumer<String, String> paymentEvents;

    @BeforeEach
    void setUp() {
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(broker, "reader-" + UUID.randomUUID(), false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        paymentEvents = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(paymentEvents, SagaTopics.PAYMENT_EVENTS);
        listenerRegistry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void tearDown() {
        paymentEvents.close();
    }

    @Test
    void inventoryReservedFromInventoryService_shouldPublishPaymentCompleted() {
        ProducerRecord<String, String> record = new ProducerRecord<>(SagaTopics.INVENTORY_EVENTS, "ORD-K1",
                "{\"orderId\":\"ORD-K1\",\"productId\":\"PROD-001\",\"quantity\":3}");
        record.headers().add("__TypeId__", "inventoryReserved".getBytes(StandardCharsets.UTF_8));
        rawProducer.send(record).join();

        ConsumerRecord<String, String> completed = awaitPaymentEvent("ORD-K1", "paymentCompleted");

        assertThat(completed.value()).contains("\"transactionId\":\"TXN-");
    }

    private ConsumerRecord<String, String> awaitPaymentEvent(String orderId, String expectedTypeId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : paymentEvents.poll(Duration.ofMillis(500))) {
                if (orderId.equals(record.key()) && expectedTypeId.equals(typeId(record))) {
                    return record;
                }
            }
        }
        throw new AssertionError("No " + expectedTypeId + " event for " + orderId + " on " + SagaTopics.PAYMENT_EVENTS);
    }

    private static String typeId(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8);
    }
}
