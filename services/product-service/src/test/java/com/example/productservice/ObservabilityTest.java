package com.example.productservice;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "management.tracing.export.enabled=false"
})
@AutoConfigureMockMvc
@AutoConfigureMetrics
@AutoConfigureTracing
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void timedEndpoint_shouldBeScrapableByPrometheus() throws Exception {
        Long id = productRepository.save(new Product(null, "Laptop", 999.99)).getId();

        mockMvc.perform(get("/api/v1/products/" + id)).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("product_get_duration_seconds_count")));
    }

    @Test
    void logLinesWrittenDuringARequest_shouldBeJsonWithTheTraceId(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/products/424242")).andExpect(status().isNotFound());

        String cacheMissLine = output.getOut().lines()
                .filter(line -> line.contains("[CACHE MISS] Loading product 424242"))
                .findFirst()
                .orElseThrow();
        assertThat(cacheMissLine).startsWith("{").contains("\"traceId\":\"", "\"spanId\":\"");
    }
}
