package com.akbal.kips.be.service.product.impl;

import com.akbal.kips.be.constants.CacheNames;
import com.akbal.kips.be.domain.product.ProductCategory;
import com.akbal.kips.be.dto.product.request.CategoryCreateRequest;
import com.akbal.kips.be.dto.product.request.CategoryRequest;
import com.akbal.kips.be.dto.product.response.CategoryResponse;
import com.akbal.kips.be.repository.ProductCategoryRepository;
import com.akbal.kips.be.service.product.ProductCategoryService;
import com.akbal.kips.be.service.product.mapper.ProductCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {
    private final ProductCategoryMapper mapper;
    private final ProductCategoryRepository repository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(CacheNames.ALL_CATEGORIES)
    public List<CategoryResponse> getAllCategories() {
        return mapper.toTree(repository.findAllByOrderByIdAsc());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.ALL_CATEGORIES, allEntries = true)
    public List<CategoryResponse> createCategories(CategoryCreateRequest request) {
        return request.getCategories().stream().map(category -> {
            var parent = category.getParentId() == null ? null : repository.findById(category.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent category not found"));
            return createCategory(category, parent);
        }).toList();
    }

    private CategoryResponse createCategory(CategoryRequest request, ProductCategory parent) {
        var category = new ProductCategory();
        category.setName(request.getName());
        category.setParent(parent);
        repository.save(category);
        var response = mapper.toResponse(category);

        if (request.getSubCategories() != null) {
            for (var child : request.getSubCategories()) {
                if (child.getParentId() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nested categories inherit their parent");
                }
                mapper.appendChild(response, createCategory(child, category));
            }
        }
        return response;
    }
}
