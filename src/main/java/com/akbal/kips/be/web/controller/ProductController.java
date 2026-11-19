package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.request.ProductFilters;
import com.akbal.kips.be.dto.product.request.ProductUpdateRequest;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.service.product.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springdoc.core.annotations.ParameterObject;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController extends BaseController {

    private final ProductService service;

    @PostMapping
    public ApiResponse<ProductResponse> createProduct(
            @RequestBody @Valid ProductRequest request) {

        var product = service.createProduct(request);
        return respond(product);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(
            @PathVariable Long id) {

        var product = service.getProductById(id);
        return respond(product);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts(
            @Valid @ModelAttribute @ParameterObject ProductFilters filters,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        var page = service.getProducts(filters, pageable);
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(respond(page.getContent()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid ProductUpdateRequest request) {

        var product = service.updateProduct(id, request);
        return respond(product);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(
            @PathVariable Long id) {

        service.deleteProduct(id);
        return respond(null);
    }
}
