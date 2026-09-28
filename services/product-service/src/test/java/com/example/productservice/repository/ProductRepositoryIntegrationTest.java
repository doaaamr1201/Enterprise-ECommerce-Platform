package com.example.productservice.repository;

import com.example.productservice.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProductRepository productRepository;

    @Test
    void save_andFindById_roundTrip() {
        Product saved = productRepository.save(new Product(null, "Laptop", 999.99));

        assertThat(productRepository.findById(saved.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getName()).isEqualTo("Laptop");
                    assertThat(found.getPrice()).isEqualTo(999.99);
                });
    }

    @Test
    void findByPriceLessThan_returnsMatchingProducts() {
        productRepository.saveAll(List.of(
                new Product(null, "Mouse", 29.99),
                new Product(null, "Monitor", 399.99)));

        List<Product> cheap = productRepository.findByPriceLessThan(50);

        assertThat(cheap).extracting(Product::getName).containsExactly("Mouse");
    }
}
