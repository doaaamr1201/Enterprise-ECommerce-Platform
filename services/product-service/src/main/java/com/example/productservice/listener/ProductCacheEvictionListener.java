package com.example.productservice.listener;

import com.example.productservice.event.ProductChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductCacheEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheEvictionListener.class);

    private final CacheManager cacheManager;

    public ProductCacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onProductChanged(ProductChangedEvent event) {
        Cache products = cacheManager.getCache("products");
        if (products != null) {
            products.evict(event.productId());
            products.evict("all");
        }
        log.info("[CQRS] Cache evicted for product {} due to {}", event.productId(), event.changeType());
    }
}
