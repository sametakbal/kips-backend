package com.akbal.kips.be.service.staff;

import com.akbal.kips.be.dto.staff.StaffDtos.FulfillmentItem;
import com.akbal.kips.be.dto.staff.StaffDtos.FulfillmentOrder;
import com.akbal.kips.be.dto.staff.StaffDtos.FulfillmentSummary;
import com.akbal.kips.be.dto.staff.StaffDtos.SupportOrder;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.commerce.CommerceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffOrderService {
    private final JdbcClient jdbc;
    private final CommerceService commerce;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + Permissions.ORDER_READ_FULFILLMENT + "')")
    public List<FulfillmentSummary> getFulfillmentQueue(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return jdbc.sql("""
                SELECT id, status, created_at FROM shop_order
                WHERE status = 'PAID' ORDER BY id LIMIT :size OFFSET :offset
                """).param("size", size).param("offset", (long) page * size)
                .query(FulfillmentSummary.class).list();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + Permissions.ORDER_READ_FULFILLMENT + "')")
    public FulfillmentOrder getFulfillmentOrder(long id) {
        var summary = jdbc.sql("SELECT id, status, created_at FROM shop_order WHERE id = :id")
                .param("id", id).query(FulfillmentSummary.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        var items = jdbc.sql("""
                SELECT product_id, product_name, quantity FROM shop_order_item
                WHERE order_id = :id ORDER BY product_id
                """).param("id", id).query(FulfillmentItem.class).list();
        return new FulfillmentOrder(summary, items);
    }

    @PreAuthorize("hasAuthority('" + Permissions.ORDER_SHIP + "')")
    public FulfillmentOrder ship(long id) {
        var order = commerce.shipOrder(id);
        var summary = order.summary();
        var items = order.items().stream()
                .map(item -> new FulfillmentItem(item.productId(), item.productName(), item.quantity()))
                .toList();
        return new FulfillmentOrder(new FulfillmentSummary(summary.id(), summary.status(), summary.createdAt()), items);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + Permissions.ORDER_READ_SUPPORT + "')")
    public SupportOrder getSupportOrder(long id) {
        return jdbc.sql("""
                SELECT o.id, o.status, o.created_at, LEFT(u.email, 1) || '***@***' AS masked_email
                FROM shop_order o JOIN app_user u ON u.id = o.user_id
                WHERE o.id = :id
                """).param("id", id).query(SupportOrder.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
