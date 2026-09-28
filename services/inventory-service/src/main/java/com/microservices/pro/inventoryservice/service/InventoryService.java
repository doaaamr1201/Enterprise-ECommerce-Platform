package com.microservices.pro.inventoryservice.service;

import com.microservices.pro.inventoryservice.exception.InsufficientStockException;
import com.microservices.pro.inventoryservice.model.Reservation;
import com.microservices.pro.inventoryservice.model.StockCheckResponse;
import com.microservices.pro.inventoryservice.model.StockItem;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService {

    private final Map<String, StockItem> inventory = new ConcurrentHashMap<>(Map.of(
            "PROD-001", new StockItem("PROD-001", 100, 0),
            "PROD-002", new StockItem("PROD-002", 5, 0),
            "PROD-003", new StockItem("PROD-003", 0, 0)
    ));

    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();

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

    public synchronized void reserveStock(String productId, int quantity, String orderId) {
        if (reservations.containsKey(orderId)) {
            return;
        }
        StockItem item = inventory.getOrDefault(productId, new StockItem(productId, 0, 0));
        if (!item.hasStock(quantity)) {
            throw new InsufficientStockException("Insufficient stock for " + productId);
        }
        inventory.put(productId, new StockItem(productId, item.availableQuantity(), item.reservedQuantity() + quantity));
        reservations.put(orderId, new Reservation(productId, quantity));
    }

    public synchronized boolean releaseStock(String orderId) {
        Reservation reservation = reservations.remove(orderId);
        if (reservation == null) {
            return false;
        }
        inventory.computeIfPresent(reservation.productId(), (productId, item) ->
                new StockItem(productId, item.availableQuantity(), item.reservedQuantity() - reservation.quantity()));
        return true;
    }
}
