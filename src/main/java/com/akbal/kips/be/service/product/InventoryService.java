package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.staff.StaffDtos.InventoryLevel;
import com.akbal.kips.be.dto.staff.StaffDtos.StockAdjustment;
import com.akbal.kips.be.repository.ProductRepository;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.staff.StaffAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ProductRepository products;
    private final JdbcClient jdbc;
    private final StaffAudit audit;

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_ADJUST + "') and #actorId == authentication.principal.id")
    public InventoryLevel adjustStock(long productId, long actorId, String key, StockAdjustment adjustment) {
        if (key.isBlank() || key.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must have 1 to 128 characters");
        }
        if (adjustment.delta() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock adjustment must be nonzero");
        }
        var product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        var receipt = jdbc.sql("""
                SELECT delta, reason, resulting_stock FROM inventory_adjustment_receipt
                WHERE product_id = :product AND actor_user_id = :actor AND request_key = :key
                """).param("product", productId).param("actor", actorId).param("key", key)
                .query(Receipt.class).optional();
        if (receipt.isPresent()) {
            var previous = receipt.get();
            if (previous.delta() != adjustment.delta() || !previous.reason().equals(adjustment.reason())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key is already used for another adjustment");
            }
            return new InventoryLevel(productId, previous.resultingStock());
        }

        int previous = product.getStock();
        long current = (long) previous + adjustment.delta();
        if (current < 0 || current > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock adjustment is outside the allowed range");
        }
        product.setStock((int) current);
        jdbc.sql("""
                INSERT INTO inventory_adjustment_receipt (product_id, actor_user_id, request_key, delta, reason, resulting_stock)
                VALUES (:product, :actor, :key, :delta, :reason, :stock)
                """).param("product", productId).param("actor", actorId).param("key", key)
                .param("delta", adjustment.delta()).param("reason", adjustment.reason())
                .param("stock", product.getStock()).update();
        audit.record("STOCK_ADJUSTED", "PRODUCT", productId, previous, product.getStock(), adjustment.reason());
        return new InventoryLevel(productId, product.getStock());
    }

    private record Receipt(int delta, String reason, int resultingStock) {
    }
}
