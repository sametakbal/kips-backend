package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.request.ProductFilters;
import com.akbal.kips.be.dto.product.request.ProductMetadata;
import com.akbal.kips.be.dto.product.request.ProductUpdateRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.ProductContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long id);

    Page<ProductResponse> getProducts(ProductFilters filters, Pageable pageable);

    ProductResponse updateProduct(Long id, ProductUpdateRequest request);

    ProductResponse replaceMetadata(Long id, ProductMetadata metadata);

    ProductResponse updateContent(Long id, ProductContent request);

    void deleteProduct(Long id);
}
