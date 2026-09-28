package com.example.productservice.controller;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.InvalidProductException;
import com.example.productservice.model.Product;
import com.example.productservice.projection.ProductSummaryProjection;
import com.example.productservice.service.ProductCommandService;
import com.example.productservice.service.ProductQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductCommandService productCommandService;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    @DisplayName("GET /api/products/{id} returns 200 with the flattened summary")
    void getProduct_found_returns200() throws Exception {
        when(productQueryService.findById(1L))
                .thenReturn(Optional.of(new ProductSummaryProjection(1L, "Laptop", 999.99, "ELECTRONICS")));

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.categoryName").value("ELECTRONICS"));
    }

    @Test
    @DisplayName("Unversioned /api/products is no longer served by the service")
    void unversionedPath_returns404() throws Exception {
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 404 when not found")
    void getProduct_notFound_returns404() throws Exception {
        when(productQueryService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/products returns 201 with the created product")
    void createProduct_valid_returns201() throws Exception {
        when(productCommandService.create(any(ProductRequest.class))).thenReturn(new Product(7L, "Monitor", 399.99));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Monitor\",\"price\":399.99,\"category\":\"ELECTRONICS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Monitor"))
                .andExpect(jsonPath("$.price").value(399.99));
    }

    @Test
    @DisplayName("POST /api/products with missing name returns 400")
    void createProduct_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":50}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productCommandService);
    }

    @Test
    @DisplayName("POST /api/products with a non-positive price returns 400")
    void createProduct_invalidPrice_returns400() throws Exception {
        when(productCommandService.create(any(ProductRequest.class)))
                .thenThrow(new InvalidProductException("Price must be positive"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"price\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
