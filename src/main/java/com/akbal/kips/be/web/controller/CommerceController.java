package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.commerce.CommerceDtos.Cart;
import com.akbal.kips.be.dto.commerce.CommerceDtos.Order;
import com.akbal.kips.be.dto.commerce.CommerceDtos.OrderSummary;
import com.akbal.kips.be.dto.commerce.CommerceDtos.QuantityRequest;
import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.service.commerce.CommerceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CommerceController extends BaseController {
    private final CommerceService service;

    @GetMapping("/cart")
    public ApiResponse<Cart> getCart(Principal user) {
        return respond(service.getCart(user.getName()));
    }

    @PutMapping("/cart/items/{productId}")
    public ApiResponse<Cart> setItem(
            Principal user, @PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        return respond(service.setQuantity(user.getName(), productId, request.quantity()));
    }

    @DeleteMapping("/cart/items/{productId}")
    public ApiResponse<Cart> removeItem(Principal user, @PathVariable long productId) {
        return respond(service.removeItem(user.getName(), productId));
    }

    @PostMapping("/orders/checkout")
    public ApiResponse<Order> checkout(Principal user, @RequestHeader("Idempotency-Key") String key) {
        return respond(service.checkout(user.getName(), key));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderSummary>> getOrders(Principal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return respond(service.getOrders(user.getName(), page, size));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<Order> getOrder(Principal user, @PathVariable long orderId) {
        return respond(service.getOrder(user.getName(), orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Order> cancelOrder(Principal user, @PathVariable long orderId) {
        return respond(service.cancelOrder(user.getName(), orderId));
    }

    @PostMapping("/admin/orders/{orderId}/ship")
    public ApiResponse<Order> shipOrder(@PathVariable long orderId) {
        return respond(service.shipOrder(orderId));
    }
}
