package com.example.productservice.service;

import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @CacheEvict(value = "products", key = "'all'")
    public Product createProduct(Product product) {
        product.setId(null);
        return productRepository.save(product);
    }

    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public Optional<Product> getProductById(Long id) {
        log.info("[CACHE MISS] Loading product {} from repository", id);
        return productRepository.findById(id);
    }

    @Cacheable(value = "products", key = "'all'")
    public List<Product> getAllProducts() {
        log.info("[CACHE MISS] Loading all products from repository");
        return productRepository.findAll();
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "products", key = "'all'")
    })
    public boolean deleteProduct(Long id) {
        log.info("[CACHE EVICT] Invalidating cache for product {}", id);
        return productRepository.deleteById(id);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#product.id"),
            @CacheEvict(value = "products", key = "'all'")
    })
    public Product updateProduct(Product product) {
        if (product.getId() == null || productRepository.findById(product.getId()).isEmpty()) {
            return null;
        }
        log.info("[CACHE EVICT] Invalidating cache for product {}", product.getId());
        return productRepository.save(product);
    }
}
