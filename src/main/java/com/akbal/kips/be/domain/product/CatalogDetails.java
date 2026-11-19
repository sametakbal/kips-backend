package com.akbal.kips.be.domain.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CatalogDetails(
        @Size(max = 120) String brand,
        @Size(max = 120) String manufacturer,
        @Size(max = 120) String model,
        @Size(max = 120) String manufacturerPartNumber,
        @Pattern(regexp = "[A-Z]{2}") String countryOfOrigin,
        Condition condition,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate,
        @Valid PackageDimensions packageDimensions,
        @Size(max = 20) List<@NotBlank @Size(max = 2048) String> imageUrls,
        @Size(max = 100) Map<@NotBlank @Size(max = 64) String, @NotBlank @Size(max = 512) String> attributes,
        @JsonProperty("variantOptions") @Size(max = 10)
        Map<@NotBlank @Size(max = 64) String, @NotBlank @Size(max = 128) String> variantOptions) {

    public enum Condition {
        NEW, USED, REFURBISHED
    }

    public record PackageDimensions(
            @Positive Integer lengthMm, @Positive Integer widthMm,
            @Positive Integer heightMm, @Positive Integer weightGrams) {
    }
}
