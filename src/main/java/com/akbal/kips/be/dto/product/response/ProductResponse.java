package com.akbal.kips.be.dto.product.response;

import com.akbal.kips.be.dto.product.request.ProductMetadata;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductResponse {

    private Long id;
    private String sku;
    private ProductMetadata metadata;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Long categoryId;
}
