package com.akbal.kips.be.dto.staff;

import com.akbal.kips.be.security.StaffPermission;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class StaffDtos {
    private StaffDtos() {
    }

    public enum StaffRole {
        ROLE_CATALOG_STAFF, ROLE_FULFILLMENT_STAFF, ROLE_SUPPORT_AGENT
    }

    public record RoleAssignment(String username, List<String> roles) {
    }

    public record StaffPermissionCatalog(List<StaffRole> roles, List<StaffPermission> permissions) {
    }

    public record StaffRolePermissions(StaffRole role, List<StaffPermission> permissions) {
    }

    public record ReplacePermissions(@NotNull Set<@NotNull StaffPermission> permissions) {
    }

    public record ProductContent(@NotBlank @Size(max = 255) String name, String description,
                                 @NotNull @Positive Long categoryId) {
        @JsonAnySetter
        public void rejectUnknownField(String field, Object value) {
            throw new IllegalArgumentException("Unsupported product content field");
        }
    }

    public record StockAdjustment(@NotNull Integer delta, @NotBlank @Size(max = 500) String reason) {
    }

    public record InventoryLevel(long productId, int stock) {
    }

    public record FulfillmentSummary(long id, String status, OffsetDateTime createdAt) {
    }

    public record FulfillmentItem(long productId, String productName, int quantity) {
    }

    public record FulfillmentOrder(FulfillmentSummary summary, List<FulfillmentItem> items) {
    }

    public record SupportOrder(long id, String status, OffsetDateTime createdAt, String maskedEmail) {
    }
}
