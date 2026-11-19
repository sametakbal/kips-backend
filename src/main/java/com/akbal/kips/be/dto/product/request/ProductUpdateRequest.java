package com.akbal.kips.be.dto.product.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@JsonIgnoreProperties("stock")
public record ProductUpdateRequest(
        @Size(max = 64) String sku,
        @Valid ProductMetadata metadata,
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull @Positive Long categoryId) {
}
