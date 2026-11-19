package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.InventoryLevel;
import com.akbal.kips.be.dto.staff.StaffDtos.ProductContent;
import com.akbal.kips.be.dto.staff.StaffDtos.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(ProductRequest request);

    ProductResponse getProductById(Long id);

    Page<ProductResponse> getProducts(Long categoryId, Pageable pageable);

    ProductResponse updateProduct(Long id, ProductRequest request);

    ProductResponse updateContent(Long id, ProductContent request);

    InventoryLevel adjustStock(Long id, StockAdjustment adjustment);

    void deleteProduct(Long id);
}
