package com.akbal.kips.be.service.commerce;

import com.akbal.kips.be.dto.commerce.WishlistDtos.Wishlist;
import com.akbal.kips.be.dto.commerce.WishlistDtos.WishlistItem;
import com.akbal.kips.be.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.SHOP_OWN + "') and #username == authentication.name")
public class WishlistService {
    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public Wishlist getWishlist(String username) {
        return readWishlist(userId(username));
    }

    @Transactional
    public Wishlist addItem(String username, long productId) {
        long userId = userId(username);
        jdbc.sql("SELECT id FROM product WHERE id = :id FOR KEY SHARE").param("id", productId)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        jdbc.sql("""
                INSERT INTO wishlist_item(user_id, product_id) VALUES (:user, :product)
                ON CONFLICT (user_id, product_id) DO NOTHING
                """).param("user", userId).param("product", productId).update();
        return readWishlist(userId);
    }

    @Transactional
    public Wishlist removeItem(String username, long productId) {
        long userId = userId(username);
        jdbc.sql("DELETE FROM wishlist_item WHERE user_id = :user AND product_id = :product")
                .param("user", userId).param("product", productId).update();
        return readWishlist(userId);
    }

    private long userId(String username) {
        return jdbc.sql("SELECT id FROM app_user WHERE username = :username").param("username", username)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Wishlist readWishlist(long userId) {
        var items = jdbc.sql("""
                SELECT p.id AS product_id, p.name AS product_name, p.price AS unit_price, p.stock,
                       w.created_at AS added_at
                FROM wishlist_item w JOIN product p ON p.id = w.product_id
                WHERE w.user_id = :user ORDER BY w.created_at DESC, p.id DESC
                """).param("user", userId).query(WishlistItem.class).list();
        return new Wishlist(items);
    }
}
