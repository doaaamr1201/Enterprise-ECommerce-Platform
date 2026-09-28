package com.example.productservice.service;

import com.example.productservice.event.ProductChangedEvent;
import com.example.productservice.listener.ProductCacheEvictionListener;
import com.example.productservice.projection.ProductSummaryProjection;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
class ProductQueryCachingTest {

    @Configuration
    @EnableCaching
    static class CachingConfig {

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        ProductQueryService productQueryService(ProductRepository productRepository) {
            return new ProductQueryService(productRepository);
        }

        @Bean
        ProductCacheEvictionListener productCacheEvictionListener(CacheManager cacheManager) {
            return new ProductCacheEvictionListener(cacheManager);
        }

        @Bean
        CacheManager cacheManager() {
            ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("products");
            cacheManager.setAllowNullValues(false);
            return cacheManager;
        }
    }

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("products").clear();
        reset(productRepository);
    }

    @Test
    void findById_secondCall_shouldBeServedFromCache() {
        when(productRepository.findSummaryById(1L)).thenReturn(Optional.of(summary(1L, 999.99)));

        productQueryService.findById(1L);
        productQueryService.findById(1L);

        verify(productRepository, times(1)).findSummaryById(1L);
    }

    @Test
    void productChangedEvent_shouldEvictTheProductAndTheListSoTheNextQueryIsFresh() {
        when(productRepository.findSummaryById(1L))
                .thenReturn(Optional.of(summary(1L, 999.99)))
                .thenReturn(Optional.of(summary(1L, 899.99)));
        when(productRepository.findAllSummaries())
                .thenReturn(List.of(summary(1L, 999.99)))
                .thenReturn(List.of(summary(1L, 899.99)));
        productQueryService.findById(1L);
        productQueryService.findAll();

        eventPublisher.publishEvent(new ProductChangedEvent(1L, "UPDATED"));

        assertThat(productQueryService.findById(1L)).get().extracting(ProductSummaryProjection::price).isEqualTo(899.99);
        assertThat(productQueryService.findAll()).extracting(ProductSummaryProjection::price).containsExactly(899.99);
    }

    @Test
    void findById_forMissingProduct_shouldNotBeCached() {
        when(productRepository.findSummaryById(999L)).thenReturn(Optional.empty());

        assertThat(productQueryService.findById(999L)).isEmpty();
        assertThat(productQueryService.findById(999L)).isEmpty();

        verify(productRepository, times(2)).findSummaryById(999L);
    }

    private static ProductSummaryProjection summary(Long id, double price) {
        return new ProductSummaryProjection(id, "Laptop", price, "ELECTRONICS");
    }
}
