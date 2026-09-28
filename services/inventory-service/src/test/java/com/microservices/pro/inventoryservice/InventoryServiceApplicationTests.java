package com.microservices.pro.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class InventoryServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
