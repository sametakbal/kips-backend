package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.ReviewDtos.Review;
import com.akbal.kips.be.dto.product.ReviewDtos.ReviewRequest;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.service.product.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController extends BaseController {
    private final ReviewService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Review>>> getReviews(
            @PathVariable long productId, @PageableDefault(size = 20) Pageable pageable) {
        var page = service.getReviews(productId, pageable);
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(respond(page.getContent()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Review>> addReview(
            Principal user, @PathVariable long productId, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(respond(service.addReview(user.getName(), productId, request), HttpStatus.CREATED));
    }

    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(@PathVariable long productId, @PathVariable long reviewId) {
        service.deleteReview(productId, reviewId);
        return respond(null);
    }
}
