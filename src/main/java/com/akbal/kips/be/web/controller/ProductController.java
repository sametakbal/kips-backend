package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.request.ProductRequest;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.service.product.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
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
    public ApiResponse<List<ProductResponse>> getAllProducts() {

        var products = service.getAllProducts();
        return respond(products);
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid ProductRequest request) {

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