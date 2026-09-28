package com.example.productservice.service;

import com.example.productservice.projection.ProductSummaryProjection;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryService.class);

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<ProductSummaryProjection> findById(Long id) {
        log.info("[CACHE MISS] Loading product {} from repository", id);
        return productRepository.findSummaryById(id);
    }

    @Cacheable(value = "products", key = "'all'")
    @Transactional(readOnly = true)
    public List<ProductSummaryProjection> findAll() {
        log.info("[CACHE MISS] Loading all products from repository");
        return productRepository.findAllSummaries();
    }
}
