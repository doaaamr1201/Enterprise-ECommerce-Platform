package com.example.productservice.projection;

import java.io.Serializable;

public record ProductSummaryProjection(Long id, String name, double price, String categoryName) implements Serializable {

    public String displayLabel() {
        return categoryName == null ? name : name + " (" + categoryName + ")";
    }
}
