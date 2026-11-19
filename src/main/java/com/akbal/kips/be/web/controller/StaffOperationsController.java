package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.product.response.ProductResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.FulfillmentOrder;
import com.akbal.kips.be.dto.staff.StaffDtos.FulfillmentSummary;
import com.akbal.kips.be.dto.staff.StaffDtos.InventoryLevel;
import com.akbal.kips.be.dto.staff.StaffDtos.ProductContent;
import com.akbal.kips.be.dto.staff.StaffDtos.StockAdjustment;
import com.akbal.kips.be.dto.staff.StaffDtos.SupportOrder;
import com.akbal.kips.be.service.product.ProductService;
import com.akbal.kips.be.service.staff.StaffOrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/staff")
@SecurityRequirement(name = "bearerAuth")
public class StaffOperationsController extends BaseController {
    private final ProductService products;
    private final StaffOrderService orders;

    @PatchMapping("/catalog/products/{id}/content")
    public ApiResponse<ProductResponse> editProduct(@PathVariable long id, @Valid @RequestBody ProductContent request) {
        return respond(products.updateContent(id, request));
    }

    @PostMapping("/inventory/products/{id}/adjust")
    public ApiResponse<InventoryLevel> adjustStock(@PathVariable long id, @Valid @RequestBody StockAdjustment request) {
        return respond(products.adjustStock(id, request));
    }

    @GetMapping("/fulfillment/orders")
    public ApiResponse<List<FulfillmentSummary>> getFulfillmentQueue(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return respond(orders.getFulfillmentQueue(page, size));
    }

    @GetMapping("/fulfillment/orders/{id}")
    public ApiResponse<FulfillmentOrder> getFulfillmentOrder(@PathVariable long id) {
        return respond(orders.getFulfillmentOrder(id));
    }

    @PostMapping("/fulfillment/orders/{id}/ship")
    public ApiResponse<FulfillmentOrder> ship(@PathVariable long id) {
        return respond(orders.ship(id));
    }

    @GetMapping("/support/orders/{id}")
    public ApiResponse<SupportOrder> getSupportOrder(@PathVariable long id) {
        return respond(orders.getSupportOrder(id));
    }
}
