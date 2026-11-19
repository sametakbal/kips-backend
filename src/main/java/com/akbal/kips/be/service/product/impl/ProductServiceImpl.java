package com.akbal.kips.be.service.product.impl;

import com.akbal.kips.be.domain.product.Product;
import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.InventoryLevel;
import com.akbal.kips.be.dto.staff.StaffDtos.ProductContent;
import com.akbal.kips.be.dto.staff.StaffDtos.StockAdjustment;
import com.akbal.kips.be.repository.ProductCategoryRepository;
import com.akbal.kips.be.repository.ProductRepository;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.product.ProductService;
import com.akbal.kips.be.service.product.mapper.ProductMapper;
import com.akbal.kips.be.service.staff.StaffAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final StaffAudit audit;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
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
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        var product = findProductForUpdate(id);
        int previousStock = product.getStock();
        applyRequest(product, request);
        if (previousStock != product.getStock()) {
            audit.record("STOCK_ADJUSTED", "PRODUCT", id, previousStock, product.getStock(),
                    "Updated through administrator product endpoint");
        }
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_EDIT + "')")
    public ProductResponse updateContent(Long id, ProductContent request) {
        var product = findProductForUpdate(id);
        applyContent(product, request.name(), request.description(), request.categoryId());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_ADJUST + "')")
    public InventoryLevel adjustStock(Long id, StockAdjustment adjustment) {
        if (adjustment.delta() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock adjustment must be nonzero");
        }
        var product = findProductForUpdate(id);
        int previous = product.getStock();
        long current = (long) previous + adjustment.delta();
        if (current < 0 || current > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock adjustment is outside the allowed range");
        }
        product.setStock((int) current);
        audit.record("STOCK_ADJUSTED", "PRODUCT", id, previous, current, adjustment.reason());
        return new InventoryLevel(id, product.getStock());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public void deleteProduct(Long id) {
        productRepository.delete(findProductForUpdate(id));
    }

    private Product findProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private void applyRequest(Product product, ProductRequest request) {
        applyContent(product, request.getName(), request.getDescription(), request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
    }

    private void applyContent(Product product, String name, String description, Long categoryId) {
        var category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        product.setName(name);
        product.setDescription(description);
        product.setCategory(category);
    }
}
