package com.akbal.kips.be.web.controller;


import com.akbal.kips.be.dto.product.request.CategoryCreateRequest;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.product.response.CategoryResponse;
import com.akbal.kips.be.service.product.ProductCategoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController extends BaseController {

    private final ProductCategoryService service;


    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        var categories = service.getAllCategories();
        return respond(categories);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> createCategory(@RequestBody @Valid CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(respond(service.createCategories(request), HttpStatus.CREATED));
    }

}
