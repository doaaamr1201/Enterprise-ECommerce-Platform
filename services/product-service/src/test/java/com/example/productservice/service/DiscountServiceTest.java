package com.example.productservice.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountServiceTest {

    private final DiscountService discountService = new DiscountService();

    @ParameterizedTest
    @CsvSource({
            "SILVER, 5",
            "GOLD, 10",
            "PLATINUM, 15",
            "BRONZE, 0"
    })
    void calcDiscount_shouldReturnDiscountForTier(String tier, int expected) {
        assertThat(discountService.calcDiscount(tier)).isEqualTo(expected);
    }
}
