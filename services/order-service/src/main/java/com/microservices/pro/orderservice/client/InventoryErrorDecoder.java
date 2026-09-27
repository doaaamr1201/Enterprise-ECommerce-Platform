package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.exception.InsufficientStockException;
import com.microservices.pro.orderservice.exception.ProductNotFoundException;
import com.microservices.pro.orderservice.exception.ServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

@Component
public class InventoryErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 409 -> new InsufficientStockException("Product out of stock");
            case 404 -> new ProductNotFoundException("Product not found");
            case 503 -> new ServiceUnavailableException("Inventory unavailable");
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
