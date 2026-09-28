package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.security.OrderServiceTokenClient;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignJwtInterceptorTest {

    @Test
    void apply_shouldSendTheServiceTokenFromClientCredentials() {
        OrderServiceTokenClient tokenClient = mock(OrderServiceTokenClient.class);
        when(tokenClient.getAccessToken()).thenReturn("service-token");
        RequestTemplate template = new RequestTemplate();

        new FeignJwtInterceptor(tokenClient).apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer service-token");
    }
}
