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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig
class ProductServiceCachingTest {

    @Configuration
    @EnableCaching
    static class CachingConfig {

        @Bean
        ProductRepository productRepository() {
            return spy(new ProductRepository());
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
    void clearCache() {
        cacheManager.getCache("products").clear();
        clearInvocations(productRepository);
    }

    @Test
    void getProductById_secondCall_shouldBeServedFromCache() {
        Long id = productService.createProduct(new Product(null, "Laptop", 1500.0)).getId();

        productService.getProductById(id);
        productService.getProductById(id);

        verify(productRepository, times(1)).findById(id);
    }

    @Test
    void updateProduct_shouldEvictSoNextReadIsFresh() {
        Long id = productService.createProduct(new Product(null, "Mouse", 200.0)).getId();
        productService.getProductById(id);

        productService.updateProduct(new Product(id, "Mouse", 150.0));

        assertThat(productService.getProductById(id)).get().extracting(Product::getPrice).isEqualTo(150.0);
    }

    @Test
    void getAllProducts_shouldBeCachedAndEvictedWhenAProductIsCreated() {
        productService.getAllProducts();
        productService.getAllProducts();
        verify(productRepository, times(1)).findAll();

        Product keyboard = productService.createProduct(new Product(null, "Keyboard", 300.0));

        assertThat(productService.getAllProducts()).extracting(Product::getId).contains(keyboard.getId());
    }

    @Test
    void getProductById_forMissingProduct_shouldReturnEmptyWithoutCachingIt() {
        assertThat(productService.getProductById(999L)).isEmpty();
        assertThat(productService.getProductById(999L)).isEmpty();

        verify(productRepository, times(2)).findById(999L);
    }
}
