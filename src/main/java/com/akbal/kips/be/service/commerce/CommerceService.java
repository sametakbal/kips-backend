package com.akbal.kips.be.service.commerce;

import com.akbal.kips.be.dto.commerce.CommerceDtos.Cart;
import com.akbal.kips.be.dto.commerce.CommerceDtos.Item;
import com.akbal.kips.be.dto.commerce.CommerceDtos.Order;
import com.akbal.kips.be.dto.commerce.CommerceDtos.OrderSummary;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.staff.StaffAudit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@PreAuthorize("hasAuthority('" + Permissions.SHOP_OWN + "') and #username == authentication.name")
public class CommerceService {
    private static final String ORDER_FIELDS = "id, status, payment_method, currency, total, created_at";
    private static final String CART_ITEMS = """
            SELECT p.id AS product_id, p.name AS product_name, p.price AS unit_price, item.quantity
            FROM cart_item item JOIN product p ON item.product_id = p.id
            WHERE item.user_id = :user ORDER BY p.id
            """;
    private static final String CURRENCY = "TRY";

    private final JdbcClient jdbc;
    private final StaffAudit audit;
    private final boolean localPaymentEnabled;

    public CommerceService(JdbcClient jdbc, StaffAudit audit,
            @Value("${checkout.local-test-enabled:false}") boolean localPaymentEnabled) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.localPaymentEnabled = localPaymentEnabled;
    }

    @Transactional(readOnly = true)
    public Cart getCart(String username) {
        return readCart(userId(username, false));
    }

    @Transactional
    public Cart setQuantity(String username, long productId, int quantity) {
        long userId = userId(username, true);
        jdbc.sql("SELECT id FROM product WHERE id = :id FOR KEY SHARE").param("id", productId)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        jdbc.sql("""
                INSERT INTO cart_item(user_id, product_id, quantity) VALUES (:user, :product, :quantity)
                ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity
                """)
                .param("user", userId).param("product", productId).param("quantity", quantity).update();
        return readCart(userId);
    }

    @Transactional
    public Cart removeItem(String username, long productId) {
        long userId = userId(username, true);
        jdbc.sql("DELETE FROM cart_item WHERE user_id = :user AND product_id = :product")
                .param("user", userId).param("product", productId).update();
        return readCart(userId);
    }

    @Transactional
    public Order checkout(String username, String key) {
        if (!localPaymentEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Local test payment is disabled");
        }
        if (key.isBlank() || key.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must have 1 to 128 characters");
        }
        long userId = userId(username, true);
        var previous = jdbc.sql("SELECT " + ORDER_FIELDS + """
                 FROM shop_order WHERE user_id = :user AND idempotency_key = :key
                """).param("user", userId).param("key", key).query(OrderSummary.class).optional();
        if (previous.isPresent()) {
            return readOrder(previous.get());
        }

        var items = jdbc.sql(CART_ITEMS + " FOR UPDATE OF p").param("user", userId).query(Item.class).list();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        }
        for (var item : items) {
            var updated = jdbc.sql("""
                    UPDATE product SET stock = stock - :quantity, updated_at = CURRENT_TIMESTAMP
                    WHERE id = :product AND stock >= :quantity AND price >= 0 AND category_id IS NOT NULL
                    """).param("product", item.productId()).param("quantity", item.quantity()).update();
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is unavailable or has insufficient stock");
            }
        }
        var summary = jdbc.sql("""
                INSERT INTO shop_order(user_id, idempotency_key, status, payment_method, currency, total)
                VALUES (:user, :key, 'PAID', 'LOCAL_TEST', :currency, :total)
                RETURNING
                """ + ORDER_FIELDS)
                .param("user", userId).param("key", key).param("currency", CURRENCY).param("total", total(items))
                .query(OrderSummary.class).single();
        for (var item : items) {
            jdbc.sql("""
                    INSERT INTO shop_order_item(order_id, product_id, product_name, unit_price, quantity)
                    VALUES (:order, :product, :name, :price, :quantity)
                    """).param("order", summary.id()).param("product", item.productId())
                    .param("name", item.productName()).param("price", item.unitPrice())
                    .param("quantity", item.quantity()).update();
        }
        jdbc.sql("DELETE FROM cart_item WHERE user_id = :user").param("user", userId).update();
        return new Order(summary, items);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> getOrders(String username, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return jdbc.sql("SELECT " + ORDER_FIELDS + """
                 FROM shop_order WHERE user_id = :user ORDER BY id DESC LIMIT :size OFFSET :offset
                """).param("user", userId(username, false)).param("size", size).param("offset", (long) page * size)
                .query(OrderSummary.class).list();
    }

    @Transactional(readOnly = true)
    public Order getOrder(String username, long orderId) {
        var summary = findOwnOrder(userId(username, false), orderId, false);
        return readOrder(summary);
    }

    @Transactional
    public Order cancelOrder(String username, long orderId) {
        var order = readOrder(findOwnOrder(userId(username, true), orderId, true));
        if ("CANCELLED".equals(order.summary().status())) {
            return order;
        }
        var cancelled = changePaidOrderStatus(orderId, "CANCELLED");
        for (var item : order.items()) {
            var updated = jdbc.sql("""
                    UPDATE product SET stock = stock + :quantity, updated_at = CURRENT_TIMESTAMP
                    WHERE id = :product AND stock <= 2147483647 - :quantity
                    """).param("quantity", item.quantity()).param("product", item.productId()).update();
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to restore product stock");
            }
        }
        return new Order(cancelled, order.items());
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.ORDER_SHIP + "')")
    public Order shipOrder(long orderId) {
        var summary = jdbc.sql("SELECT " + ORDER_FIELDS + " FROM shop_order WHERE id = :id FOR UPDATE")
                .param("id", orderId).query(OrderSummary.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"SHIPPED".equals(summary.status())) {
            var previousStatus = summary.status();
            summary = changePaidOrderStatus(orderId, "SHIPPED");
            audit.record("ORDER_SHIPPED", "ORDER", orderId, previousStatus, summary.status(), null);
        }
        return readOrder(summary);
    }

    private OrderSummary changePaidOrderStatus(long orderId, String status) {
        return jdbc.sql("""
                UPDATE shop_order SET status = :status WHERE id = :id AND status = 'PAID'
                RETURNING
                """ + ORDER_FIELDS)
                .param("id", orderId).param("status", status)
                .query(OrderSummary.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Order status cannot be changed"));
    }

    private long userId(String username, boolean lock) {
        var sql = "SELECT id FROM app_user WHERE username = :username" + (lock ? " FOR UPDATE" : "");
        return jdbc.sql(sql).param("username", username).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Cart readCart(long userId) {
        var items = jdbc.sql(CART_ITEMS).param("user", userId).query(Item.class).list();
        return new Cart(items, CURRENCY, total(items));
    }

    private OrderSummary findOwnOrder(long userId, long orderId, boolean lock) {
        var sql = "SELECT " + ORDER_FIELDS + " FROM shop_order WHERE id = :id AND user_id = :user";
        return jdbc.sql(sql + (lock ? " FOR UPDATE" : "")).param("id", orderId).param("user", userId)
                .query(OrderSummary.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private Order readOrder(OrderSummary summary) {
        var items = jdbc.sql("""
                SELECT product_id, product_name, unit_price, quantity
                FROM shop_order_item WHERE order_id = :id ORDER BY product_id
                """).param("id", summary.id()).query(Item.class).list();
        return new Order(summary, items);
    }

    private BigDecimal total(List<Item> items) {
        return items.stream().map(Item::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
