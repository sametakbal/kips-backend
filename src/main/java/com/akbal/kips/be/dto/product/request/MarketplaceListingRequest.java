package com.akbal.kips.be.dto.product.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record MarketplaceListingRequest(
        @NotBlank @Size(max = 255) String channelSku,
        @Size(max = 64) String channelBarcode,
        @Size(max = 128) String externalCatalogId,
        @Size(max = 128) String externalListingId,
        @Size(max = 128) String categoryReference,
        @Size(max = 128) String brandReference,
        @Size(max = 128) String productType,
        @Size(max = 128) String groupReference,
        @PositiveOrZero @Digits(integer = 17, fraction = 4) BigDecimal salePrice,
        @PositiveOrZero @Digits(integer = 17, fraction = 4) BigDecimal listPrice,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 100) Map<@NotBlank @Size(max = 128) String, Object> channelAttributes) {
}
