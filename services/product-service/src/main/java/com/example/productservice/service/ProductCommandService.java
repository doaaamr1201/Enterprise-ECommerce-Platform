package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.event.ProductChangedEvent;
import com.example.productservice.exception.InvalidProductException;
import com.example.productservice.model.Category;
import com.example.productservice.model.Product;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductCommandService(ProductRepository productRepository,
                                 CategoryRepository categoryRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Product create(ProductRequest request) {
        Product product = new Product(null, request.name(), requirePositivePrice(request.price()));
        product.setCategoryId(resolveCategoryId(request.category()));
        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductChangedEvent(saved.getId(), "CREATED"));
        return saved;
    }

    @Transactional
    public Optional<Product> update(Long id, ProductRequest request) {
        if (!productRepository.existsById(id)) {
            return Optional.empty();
        }
        Product product = new Product(id, request.name(), requirePositivePrice(request.price()));
        product.setCategoryId(resolveCategoryId(request.category()));
        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductChangedEvent(saved.getId(), "UPDATED"));
        return Optional.of(saved);
    }

    @Transactional
    public boolean deleteById(Long id) {
        if (!productRepository.existsById(id)) {
            return false;
        }
        productRepository.deleteById(id);
        eventPublisher.publishEvent(new ProductChangedEvent(id, "DELETED"));
        return true;
    }

    private double requirePositivePrice(double price) {
        if (price <= 0) {
            throw new InvalidProductException("Price must be positive");
        }
        return price;
    }

    private Long resolveCategoryId(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        return categoryRepository.findByName(categoryName)
                .orElseGet(() -> categoryRepository.save(new Category(categoryName)))
                .getId();
    }
}
