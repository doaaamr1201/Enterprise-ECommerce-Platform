package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.event.ProductChangedEvent;
import com.example.productservice.exception.InvalidProductException;
import com.example.productservice.model.Category;
import com.example.productservice.model.Product;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandService productCommandService;

    @ParameterizedTest
    @ValueSource(doubles = {0, -1})
    void create_withNonPositivePrice_shouldBeRejectedWithoutSavingOrPublishing(double price) {
        assertThatThrownBy(() -> productCommandService.create(new ProductRequest("Bad", price, null)))
                .isInstanceOf(InvalidProductException.class)
                .hasMessage("Price must be positive");

        verify(productRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void create_shouldSaveTheProductAndPublishCreatedEvent() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(42L);
            return product;
        });
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);

        productCommandService.create(new ProductRequest("Laptop", 999.99, null));

        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Laptop");
        assertThat(saved.getValue().getPrice()).isEqualTo(999.99);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(42L, "CREATED"));
    }

    @Test
    void create_withNewCategory_shouldCreateTheCategoryAndLinkIt() {
        when(categoryRepository.findByName("ELECTRONICS")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = new Category("ELECTRONICS");
            ReflectionTestUtils.setField(category, "id", 7L);
            return category;
        });
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = productCommandService.create(new ProductRequest("Laptop", 999.99, "ELECTRONICS"));

        assertThat(created.getCategoryId()).isEqualTo(7L);
    }

    @Test
    void update_ofMissingProduct_shouldReturnEmptyAndPublishNothing() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThat(productCommandService.update(99L, new ProductRequest("Laptop", 10, null))).isEmpty();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteById_shouldPublishDeletedEvent() {
        when(productRepository.existsById(5L)).thenReturn(true);

        assertThat(productCommandService.deleteById(5L)).isTrue();

        verify(productRepository).deleteById(5L);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(5L, "DELETED"));
    }
}
