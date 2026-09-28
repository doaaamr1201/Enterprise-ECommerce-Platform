package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.security.OrderServiceTokenClient;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class FeignJwtInterceptor implements RequestInterceptor {

    private final OrderServiceTokenClient tokenClient;

    public FeignJwtInterceptor(OrderServiceTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenClient.getAccessToken());
    }
}
