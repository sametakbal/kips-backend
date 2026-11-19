package com.akbal.kips.be.service.product.impl;

import com.akbal.kips.be.domain.product.Product;
import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.repository.ProductCategoryRepository;
import com.akbal.kips.be.repository.ProductRepository;
import com.akbal.kips.be.service.product.ProductService;
import com.akbal.kips.be.service.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "price", "stock");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        var product = new Product();
        applyRequest(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return productRepository.findById(id).map(productMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Long categoryId, Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported product sort field");
        }
        if (categoryId != null && categoryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category ID");
        }
        var sort = pageable.getSort();
        var stablePage = sort.getOrderFor("id") == null
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")))
                : pageable;
        var products = categoryId == null ? productRepository.findAll(stablePage)
                : productRepository.findAllByCategoryId(categoryId, stablePage);
        return products.map(productMapper::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        var product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        applyRequest(product, request);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        var product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        var category = productCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(category);
    }
}
