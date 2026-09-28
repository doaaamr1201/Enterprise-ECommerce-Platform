package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.dto.OrderRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentUrl;

    public PaymentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${payment.service.url:http://localhost:8083/api/payments}") String paymentUrl
    ) {
        this.restTemplate = restTemplateBuilder.build();
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
