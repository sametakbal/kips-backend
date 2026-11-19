package com.akbal.kips.be.dto.product.response;

import com.akbal.kips.be.domain.product.MarketplaceChannel;

import java.math.BigDecimal;
import java.util.Map;

public record MarketplaceListingResponse(
        Long id, Long productId, MarketplaceChannel channel, String accountKey, String marketKey,
        String channelSku, String channelBarcode, String externalCatalogId, String externalListingId,
        String categoryReference, String brandReference, String productType, String groupReference,
        BigDecimal salePrice, BigDecimal listPrice, String currency, Map<String, Object> channelAttributes) {
}
