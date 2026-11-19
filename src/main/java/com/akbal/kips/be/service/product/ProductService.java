package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long id);

    Page<ProductResponse> getProducts(Long categoryId, Pageable pageable);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}