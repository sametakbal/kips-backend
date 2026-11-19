package com.akbal.kips.be.service.product.mapper;

import com.akbal.kips.be.domain.product.Product;
import com.akbal.kips.be.dto.product.request.ProductMetadata;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .metadata(metadata(product))
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .categoryId(
                        product.getCategory() != null
                                ? product.getCategory().getId()
                                : null
                )
                .build();
    }

    private ProductMetadata metadata(Product product) {
        if (product.getGtin() == null && product.getFamily() == null && product.getCatalogDetails() == null) {
            return null;
        }
        var familyId = product.getFamily() == null ? null : product.getFamily().getId();
        return new ProductMetadata(product.getGtin(), familyId, product.getCatalogDetails());
    }
}
