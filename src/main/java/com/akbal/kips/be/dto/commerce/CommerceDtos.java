package com.akbal.kips.be.dto.commerce;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CommerceDtos {
    private CommerceDtos() {
    }

    public record QuantityRequest(@Positive int quantity) {
    }

    public record Item(Long productId, String productName, BigDecimal unitPrice, Integer quantity) {
        public BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record Cart(List<Item> items, String currency, BigDecimal total) {
    }

    public record OrderSummary(Long id, String status, String paymentMethod, String currency,
                               BigDecimal total, OffsetDateTime createdAt) {
    }

    public record Order(OrderSummary summary, List<Item> items) {
    }
}
