package com.example.productservice.repository;

import com.example.productservice.model.Product;
import com.example.productservice.projection.ProductSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByPriceLessThan(double price);

    @Query("SELECT new com.example.productservice.projection.ProductSummaryProjection(p.id, p.name, p.price, c.name) "
            + "FROM Product p LEFT JOIN Category c ON p.categoryId = c.id WHERE p.id = :id")
    Optional<ProductSummaryProjection> findSummaryById(@Param("id") Long id);

    @Query("SELECT new com.example.productservice.projection.ProductSummaryProjection(p.id, p.name, p.price, c.name) "
            + "FROM Product p LEFT JOIN Category c ON p.categoryId = c.id")
    List<ProductSummaryProjection> findAllSummaries();
}
