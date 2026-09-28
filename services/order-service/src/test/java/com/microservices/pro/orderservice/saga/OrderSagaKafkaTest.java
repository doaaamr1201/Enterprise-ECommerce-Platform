package com.microservices.pro.orderservice.saga;

import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import com.microservices.pro.orderservice.service.OrderService;
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
        "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {SagaTopics.ORDER_EVENTS, SagaTopics.INVENTORY_EVENTS, SagaTopics.PAYMENT_EVENTS})
class OrderSagaKafkaTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private KafkaTemplate<String, String> rawProducer;
    private Consumer<String, String> orderEvents;

    @BeforeEach
    void setUp() {
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(broker, "reader-" + UUID.randomUUID(), false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        orderEvents = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(orderEvents, SagaTopics.ORDER_EVENTS);
        listenerRegistry.getListenerContainers().forEach(container -> ContainerTestUtils.waitForAssignment(container, 1));
    }

    @AfterEach
    void tearDown() {
        orderEvents.close();
    }

    @Test
    void createOrder_shouldPublishOrderPlacedThatOtherServicesCanRead() {
        SagaOrderResponse response = orderService.createOrder(new OrderRequest("PROD-001", 3, 250.0), "user123");

        ConsumerRecord<String, String> placed = awaitOrderEvent(response.orderId());

        assertThat(typeId(placed)).isEqualTo("orderPlaced");
        assertThat(placed.value()).contains("\"productId\":\"PROD-001\"", "\"quantity\":3", "\"customerId\":\"user123\"");
    }

    @Test
    void paymentCompleted_shouldConfirmTheOrder() {
        String orderId = orderService.createOrder(new OrderRequest("PROD-001", 1, 10.0), "user123").orderId();

        send(SagaTopics.PAYMENT_EVENTS, orderId, "paymentCompleted",
                "{\"orderId\":\"" + orderId + "\",\"transactionId\":\"TXN-1\"}");

        awaitStatus(orderId, OrderStatus.CONFIRMED);
    }

    @Test
    void paymentFailedThenInventoryReleased_shouldCancelTheOrder() {
        String orderId = orderService.createOrder(new OrderRequest("PROD-001", 1, 10.0), "user123").orderId();

        send(SagaTopics.PAYMENT_EVENTS, orderId, "paymentFailed",
                "{\"orderId\":\"" + orderId + "\",\"reason\":\"Payment Service unavailable\"}");
        awaitStatus(orderId, OrderStatus.PAYMENT_FAILED);
        send(SagaTopics.INVENTORY_EVENTS, orderId, "inventoryReleased", "{\"orderId\":\"" + orderId + "\"}");

        awaitStatus(orderId, OrderStatus.CANCELLED);
    }

    private void send(String topic, String key, String typeId, String json) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        record.headers().add("__TypeId__", typeId.getBytes(StandardCharsets.UTF_8));
        rawProducer.send(record).join();
    }

    private ConsumerRecord<String, String> awaitOrderEvent(String orderId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : orderEvents.poll(Duration.ofMillis(500))) {
                if (orderId.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No event for " + orderId + " on " + SagaTopics.ORDER_EVENTS);
    }

    private void awaitStatus(String orderId, OrderStatus expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (orderRepository.findById(orderId).orElseThrow().getStatus() == expected) {
                return;
            }
            orderEvents.poll(Duration.ofMillis(200));
        }
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(expected);
    }

    private static String typeId(ConsumerRecord<String, String> record) {
        return new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8);
    }
}
