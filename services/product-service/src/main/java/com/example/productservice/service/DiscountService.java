package com.example.productservice.service;

import org.springframework.stereotype.Service;

@Service
public class DiscountService {

    public int calcDiscount(String tier) {
        return switch (tier) {
            case "SILVER" -> 5;
            case "GOLD" -> 10;
            case "PLATINUM" -> 15;
            default -> 0;
        };
    }
}
