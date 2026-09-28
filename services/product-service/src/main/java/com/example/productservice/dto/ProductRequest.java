package com.example.productservice.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductRequest(@NotBlank String name, double price, String category) {
}
