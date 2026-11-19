package com.akbal.kips.be.dto.product.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductFilters(
        @Positive Long categoryId,
        @Positive Long familyId,
        @Size(max = 64) String sku,
        @Size(max = 14) String gtin,
        @Size(max = 100) String q) {
}
