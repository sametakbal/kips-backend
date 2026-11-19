package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.product.request.CategoryCreateRequest;
import com.akbal.kips.be.dto.product.response.CategoryResponse;

import java.util.List;

public interface ProductCategoryService {
    List<CategoryResponse> getAllCategories();
    List<CategoryResponse> createCategories(CategoryCreateRequest request);
}
