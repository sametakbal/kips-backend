package com.akbal.kips.be.service.product.impl;

import com.akbal.kips.be.domain.product.Product;
import com.akbal.kips.be.dto.product.request.ProductFilters;
import com.akbal.kips.be.dto.product.request.ProductMetadata;
import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.request.ProductUpdateRequest;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.ProductContent;
import com.akbal.kips.be.repository.ProductCategoryRepository;
import com.akbal.kips.be.repository.ProductFamilyRepository;
import com.akbal.kips.be.repository.ProductRepository;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.product.CatalogIdentifiers;
import com.akbal.kips.be.service.product.ProductService;
import com.akbal.kips.be.service.product.mapper.ProductMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "sku", "name", "price", "stock");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductFamilyRepository productFamilies;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public ProductResponse createProduct(ProductRequest request) {
        var product = new Product();
        var sku = request.getSku() == null ? "KIPS-" + UUID.randomUUID() : request.getSku();
        product.setSku(CatalogIdentifiers.normalizedCode(sku, "SKU"));
        product.setStock(request.getStock());
        applyCatalog(product, request.getName(), request.getDescription(), request.getCategoryId(),
                request.getPrice(), request.getMetadata());
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
    public Page<ProductResponse> getProducts(ProductFilters filters, Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported product sort field");
        }
        var sort = pageable.getSort();
        var stablePage = sort.getOrderFor("id") == null
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")))
                : pageable;
        return productRepository.findAll(matching(filters), stablePage).map(productMapper::toResponse);
    }

    private Specification<Product> matching(ProductFilters filters) {
        var sku = filters.sku() == null ? null : CatalogIdentifiers.normalizedCode(filters.sku(), "SKU");
        var gtin = filters.gtin() == null ? null : CatalogIdentifiers.gtin(filters.gtin());
        var text = filters.q() == null ? "" : filters.q().strip().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            var terms = new ArrayList<Predicate>();
            if (filters.categoryId() != null) {
                terms.add(builder.equal(root.get("category").get("id"), filters.categoryId()));
            }
            if (filters.familyId() != null) {
                terms.add(builder.equal(root.get("family").get("id"), filters.familyId()));
            }
            if (sku != null) {
                terms.add(builder.equal(root.get("sku"), sku));
            }
            if (gtin != null) {
                var padded = "0".repeat(14 - gtin.length()) + gtin;
                terms.add(builder.equal(builder.function("lpad", String.class, root.get("gtin"),
                        builder.literal(14), builder.literal("0")), padded));
            }
            if (!text.isEmpty()) {
                terms.add(builder.or(builder.gt(builder.locate(builder.lower(root.get("name")), text), 0),
                        builder.gt(builder.locate(builder.lower(root.get("sku")), text), 0)));
            }
            return builder.and(terms.toArray(Predicate[]::new));
        };
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        var product = findProductForUpdate(id);
        if (request.sku() != null
                && !product.getSku().equals(CatalogIdentifiers.normalizedCode(request.sku(), "SKU"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product SKU cannot be changed");
        }
        applyCatalog(product, request.name(), request.description(), request.categoryId(),
                request.price(), request.metadata());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public ProductResponse replaceMetadata(Long id, ProductMetadata metadata) {
        var product = findProductForUpdate(id);
        applyMetadata(product, metadata);
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
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public void deleteProduct(Long id) {
        productRepository.delete(findProductForUpdate(id));
    }

    private Product findProductForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private void applyCatalog(Product product, String name, String description, Long categoryId,
            BigDecimal price, ProductMetadata metadata) {
        applyContent(product, name, description, categoryId);
        product.setPrice(price);
        if (metadata != null) {
            applyMetadata(product, metadata);
        }
    }

    private void applyMetadata(Product product, ProductMetadata metadata) {
        var details = metadata.details();
        if (details != null && details.variantOptions() != null && !details.variantOptions().isEmpty()
                && metadata.familyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant options require a product family");
        }
        if (details != null && details.imageUrls() != null && details.imageUrls().stream().anyMatch(url -> !validImage(url))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URLs must be absolute HTTP or HTTPS URLs");
        }
        var family = metadata.familyId() == null ? null : productFamilies.findById(metadata.familyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product family not found"));
        product.setFamily(family);
        product.setGtin(metadata.gtin() == null ? null : CatalogIdentifiers.gtin(metadata.gtin()));
        product.setCatalogDetails(details);
    }

    private boolean validImage(String url) {
        try {
            var uri = URI.create(url);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void applyContent(Product product, String name, String description, Long categoryId) {
        var category = productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        product.setName(name);
        product.setDescription(description);
        product.setCategory(category);
    }
}
