package com.microservices.pro.inventoryservice.service;

import com.microservices.pro.inventoryservice.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    private final InventoryService inventoryService = new InventoryService();

    @Test
    void reserveStock_shouldReduceAvailableStock() {
        inventoryService.reserveStock("PROD-002", 3, "ORD-1");

        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(2);
    }

    @Test
    void reserveStock_withoutEnoughStock_shouldThrowAndReserveNothing() {
        assertThatThrownBy(() -> inventoryService.reserveStock("PROD-002", 6, "ORD-1"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(5);
    }

    @Test
    void releaseStock_shouldRestoreTheReservationOnlyOnce() {
        inventoryService.reserveStock("PROD-002", 3, "ORD-1");

        assertThat(inventoryService.releaseStock("ORD-1")).isTrue();
        assertThat(inventoryService.releaseStock("ORD-1")).isFalse();
        assertThat(inventoryService.checkStock("PROD-002", 1).remainingStock()).isEqualTo(5);
    }
}
