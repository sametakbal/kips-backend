package com.akbal.kips.be.dto.commerce;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class WishlistDtos {
    private WishlistDtos() {
    }

    public record WishlistItem(Long productId, String productName, BigDecimal unitPrice, Integer stock,
                               OffsetDateTime addedAt) {
    }

    public record Wishlist(List<WishlistItem> items) {
    }
}
