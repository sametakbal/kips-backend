package com.akbal.kips.be.service.product.mapper;

import com.akbal.kips.be.domain.product.Product;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import org.springframework.stereotype.Component;

@Component
//"Create and manage an instance of this class for me."//
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
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
}