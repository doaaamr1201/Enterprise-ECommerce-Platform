package com.microservices.pro.orderservice.client;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "INVENTORY-SERVICE",
        path = "/api/v1/inventory"
)
public interface InventoryClient {

    @GetMapping("/check")
    StockCheckResponse checkStock(
            @RequestParam("productId") String productId,
            @RequestParam("quantity") int quantity
    );
}