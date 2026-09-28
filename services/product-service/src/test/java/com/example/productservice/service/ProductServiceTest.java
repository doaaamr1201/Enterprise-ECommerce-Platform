package com.example.productservice.service;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @ParameterizedTest
    @CsvSource({
            "SILVER, 5",
            "GOLD, 10",
            "PLATINUM, 15",
            "BRONZE, 0"
    })
    void calcDiscount_shouldReturnDiscountForTier(String tier, int expected) {
        assertThat(productService.calcDiscount(tier)).isEqualTo(expected);
    }

    @Test
    void createProduct_shouldSaveANewProductEvenIfTheRequestCarriesAnId() {
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(new Product(42L, "Laptop", 1500.0));

        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getName()).isEqualTo("Laptop");
        assertThat(saved.getValue().getPrice()).isEqualTo(1500.0);
    }

    @Test
    void getProductById_shouldReturnProduct_whenProductExists() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product(1L, "Mouse", 200.0)));

        assertThat(productService.getProductById(1L)).get().extracting(Product::getName).isEqualTo("Mouse");
    }

    @Test
    void getProductById_shouldReturnEmpty_whenProductDoesNotExist() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(productService.getProductById(999L)).isEmpty();
    }

    @Test
    void deleteProduct_shouldReturnFalseAndDeleteNothing_whenProductDoesNotExist() {
        when(productRepository.existsById(999L)).thenReturn(false);

        assertThat(productService.deleteProduct(999L)).isFalse();
        verify(productRepository, never()).deleteById(999L);
    }
}
