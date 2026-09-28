package com.microservices.pro.orderservice.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class OrderServiceTokenClient {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant expiresAt = Instant.MIN;

    @Autowired
    public OrderServiceTokenClient(
            RestClient.Builder restClientBuilder,
            @Value("${order.security.token-uri}") String tokenUri,
            @Value("${order.security.client-id}") String clientId,
            @Value("${order.security.client-secret}") String clientSecret
    ) {
        this(restClientBuilder, tokenUri, clientId, clientSecret, Clock.systemUTC());
    }

    OrderServiceTokenClient(RestClient.Builder restClientBuilder, String tokenUri, String clientId,
                            String clientSecret, Clock clock) {
        this.restClient = restClientBuilder.build();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public synchronized String getAccessToken() {
        if (cachedToken == null || !clock.instant().isBefore(expiresAt)) {
            requestToken();
        }
        return cachedToken;
    }

    private void requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<?, ?> response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Token endpoint returned no access_token");
        }
        long expiresIn = response.get("expires_in") instanceof Number seconds ? seconds.longValue() : 0;
        cachedToken = token;
        expiresAt = clock.instant().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
    }
}
