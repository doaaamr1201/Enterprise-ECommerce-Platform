package com.microservices.pro.orderservice;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.controller.OrderController;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMetrics
class ObservabilityTest {

    @Autowired
    private OrderController orderController;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private InventoryClient inventoryClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @Test
    void createOrder_shouldRecordTheTimedMetric() {
        when(inventoryClient.checkStock("PROD-001", 1)).thenReturn(new StockCheckResponse("PROD-001", 1, true, 99));
        when(paymentClient.processPayment(any())).thenReturn("Payment successful");

        orderController.createOrder(new OrderRequest("PROD-001", 1, 100.0), "user-1", "CUSTOMER").join();

        Timer timer = meterRegistry.find("order.create.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
