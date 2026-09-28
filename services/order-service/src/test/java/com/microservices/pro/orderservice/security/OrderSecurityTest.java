package com.microservices.pro.orderservice.security;

import com.microservices.pro.orderservice.controller.OrderController;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.saga.OrderSagaOrchestrator;
import com.microservices.pro.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderSecurityTest {

    private static final String BODY = "{\"productId\":\"PROD-001\",\"quantity\":1,\"amount\":100.0}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderSagaOrchestrator orderSagaOrchestrator;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void orderEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/orders/choreography").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
    }

    @Test
    void orderEndpoint_withToken_shouldUseTheUserIdTheGatewayForwarded() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class), eq("keycloak-user-id")))
                .thenReturn(new SagaOrderResponse("ORD-1", OrderStatus.PENDING, "Order received -- processing..."));

        mockMvc.perform(post("/api/orders/choreography")
                        .with(jwt().jwt(token -> token.subject("keycloak-user-id")))
                        .header("X-User-Id", "keycloak-user-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        verify(orderService).createOrder(any(OrderRequest.class), eq("keycloak-user-id"));
    }
}
