package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.commerce.WishlistDtos.Wishlist;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.service.commerce.WishlistService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wishlist")
public class WishlistController extends BaseController {
    private final WishlistService service;

    @GetMapping
    public ApiResponse<Wishlist> getWishlist(Principal user) {
        return respond(service.getWishlist(user.getName()));
    }

    @PutMapping("/items/{productId}")
    public ApiResponse<Wishlist> addItem(Principal user, @PathVariable long productId) {
        return respond(service.addItem(user.getName(), productId));
    }

    @DeleteMapping("/items/{productId}")
    public ApiResponse<Wishlist> removeItem(Principal user, @PathVariable long productId) {
        return respond(service.removeItem(user.getName(), productId));
    }
}
