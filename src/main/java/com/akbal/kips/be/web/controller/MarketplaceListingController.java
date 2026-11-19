package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.domain.product.MarketplaceChannel;
import com.akbal.kips.be.dto.product.request.MarketplaceListingRequest;
import com.akbal.kips.be.dto.product.request.ProductMetadata;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.product.response.MarketplaceListingResponse;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.service.product.MarketplaceListingService;
import com.akbal.kips.be.service.product.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/admin/products/{productId}")
public class MarketplaceListingController extends BaseController {
    private final MarketplaceListingService listings;
    private final ProductService products;

    @PutMapping("/metadata")
    public ApiResponse<ProductResponse> replaceMetadata(@PathVariable long productId,
            @Valid @RequestBody ProductMetadata metadata) {
        return respond(products.replaceMetadata(productId, metadata));
    }

    @GetMapping("/marketplace-listings")
    public ApiResponse<List<MarketplaceListingResponse>> list(@PathVariable long productId) {
        return respond(listings.list(productId));
    }

    @PutMapping("/marketplace-listings/{channel}")
    public ApiResponse<MarketplaceListingResponse> upsert(@PathVariable long productId,
            @PathVariable MarketplaceChannel channel, @RequestParam String account, @RequestParam String market,
            @Valid @RequestBody MarketplaceListingRequest request) {
        return respond(listings.upsert(productId, channel, account, market, request));
    }

    @DeleteMapping("/marketplace-listings/{channel}")
    public ApiResponse<Void> delete(@PathVariable long productId, @PathVariable MarketplaceChannel channel,
            @RequestParam String account, @RequestParam String market) {
        listings.delete(productId, channel, account, market);
        return respond(null);
    }
}
