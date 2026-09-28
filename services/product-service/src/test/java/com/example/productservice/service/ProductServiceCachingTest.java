package com.example.productservice.service;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
class ProductServiceCachingTest {

    @Configuration
    @EnableCaching
    static class CachingConfig {

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        ProductService productService(ProductRepository productRepository) {
            return new ProductService(productRepository);
        }

        @Bean
        CacheManager cacheManager() {
            ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("products");
            cacheManager.setAllowNullValues(false);
            return cacheManager;
        }
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("products").clear();
        reset(productRepository);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getProductById_secondCall_shouldBeServedFromCache() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product(1L, "Laptop", 1500.0)));

        productService.getProductById(1L);
        productService.getProductById(1L);

        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void updateProduct_shouldEvictSoNextReadIsFresh() {
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(new Product(1L, "Mouse", 200.0)))
                .thenReturn(Optional.of(new Product(1L, "Mouse", 150.0)));
        when(productRepository.existsById(1L)).thenReturn(true);
        productService.getProductById(1L);

        productService.updateProduct(new Product(1L, "Mouse", 150.0));

        assertThat(productService.getProductById(1L)).get().extracting(Product::getPrice).isEqualTo(150.0);
    }

    @Test
    void getAllProducts_shouldBeCachedAndEvictedWhenAProductIsCreated() {
        when(productRepository.findAll())
                .thenReturn(List.of(new Product(1L, "Mouse", 200.0)))
                .thenReturn(List.of(new Product(1L, "Mouse", 200.0), new Product(2L, "Keyboard", 300.0)));
        productService.getAllProducts();
        productService.getAllProducts();
        verify(productRepository, times(1)).findAll();

        productService.createProduct(new Product(null, "Keyboard", 300.0));

        assertThat(productService.getAllProducts()).extracting(Product::getName).contains("Keyboard");
    }

    @Test
    void getProductById_forMissingProduct_shouldReturnEmptyWithoutCachingIt() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(productService.getProductById(999L)).isEmpty();
        assertThat(productService.getProductById(999L)).isEmpty();

        verify(productRepository, times(2)).findById(999L);
    }
}
