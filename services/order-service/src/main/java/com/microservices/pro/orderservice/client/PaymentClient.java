package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.dto.OrderRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentUrl;

    public PaymentClient(
            @Value("${payment.service.url:http://localhost:8083/api/payments}") String paymentUrl
    ) {
        this.restTemplate = new RestTemplate();
        this.paymentUrl = paymentUrl;
    }

    public String processPayment(OrderRequest request) {
        return restTemplate.postForObject(
                paymentUrl,
                request,
                String.class
        );
    }
}
