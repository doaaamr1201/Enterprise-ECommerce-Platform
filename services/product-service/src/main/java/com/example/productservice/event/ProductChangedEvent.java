package com.example.productservice.event;

public record ProductChangedEvent(Long productId, String changeType) {
}
