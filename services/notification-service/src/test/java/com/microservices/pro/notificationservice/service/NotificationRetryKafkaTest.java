package com.microservices.pro.notificationservice.service;

import com.microservices.pro.notificationservice.event.SagaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.PAYMENT_EVENTS, SagaTopics.INVENTORY_EVENTS})
class NotificationRetryKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void eventThatKeepsFailing_shouldBeRetriedThenLandInTheDeadLetterTopic() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(broker, "dlt-reader-" + UUID.randomUUID(), false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            dlt.subscribe(List.of(SagaTopics.PAYMENT_EVENTS + ".DLT"));

            ProducerRecord<String, String> poison = new ProducerRecord<>(SagaTopics.PAYMENT_EVENTS, "ORD-X",
                    "{\"transactionId\":\"TXN-1\"}");
            poison.headers().add("__TypeId__", "paymentCompleted".getBytes(StandardCharsets.UTF_8));
            new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                    KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()))
                    .send(poison).join();

            ConsumerRecord<String, String> dead = awaitRecord(dlt, "ORD-X");

            assertThat(dead.value()).contains("TXN-1");
            assertThat(header(dead, KafkaHeaders.ORIGINAL_TOPIC)).isEqualTo(SagaTopics.PAYMENT_EVENTS);
            assertThat(header(dead, KafkaHeaders.EXCEPTION_CAUSE_FQCN)).isEqualTo(IllegalArgumentException.class.getName());
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Nothing reached the DLT for " + key);
    }
}
