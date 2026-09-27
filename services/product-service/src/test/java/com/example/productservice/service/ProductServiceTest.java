package com.example.productservice.service;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductServiceTest {

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(new ProductRepository());
    }

    @Test
    void createProduct_shouldAssignIdAndSaveProduct() {
        Product product = new Product(null, "Laptop", 1500.0);

        Product created = productService.createProduct(product);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Laptop");
        assertThat(created.getPrice()).isEqualTo(1500.0);
    }

    @Test
    void getProductById_shouldReturnProduct_whenProductExists() {
        Product created = productService.createProduct(new Product(null, "Mouse", 200.0));

        Optional<Product> found = productService.getProductById(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mouse");
    }

    @Test
    void getProductById_shouldReturnEmpty_whenProductDoesNotExist() {
        Optional<Product> found = productService.getProductById(999L);

        assertThat(found).isEmpty();
    }
}