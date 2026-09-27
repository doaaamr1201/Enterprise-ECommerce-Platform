package com.microservices.pro.inventoryservice.service;

import com.microservices.pro.inventoryservice.model.StockCheckResponse;
import com.microservices.pro.inventoryservice.model.StockItem;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InventoryService {

    private final Map<String, StockItem> inventory = Map.of(
            "PROD-001", new StockItem("PROD-001", 100, 0),
            "PROD-002", new StockItem("PROD-002", 5, 0),
            "PROD-003", new StockItem("PROD-003", 0, 0)
    );

    public StockCheckResponse checkStock(String productId, int quantity) {

        StockItem item = inventory.get(productId);

        if (item == null) {
            throw new RuntimeException("Product not found");
        }

        boolean available = item.hasStock(quantity);

        return new StockCheckResponse(productId,quantity,
                available,
                item.availableQuantity() - item.reservedQuantity()
        );
    }
}