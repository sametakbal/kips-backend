package com.akbal.kips.be.dto.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class ReviewDtos {
    private ReviewDtos() {
    }

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String comment) {
    }

    public record Review(Long id, Long productId, String username, Integer rating, String comment,
                         OffsetDateTime createdAt) {
    }
}
