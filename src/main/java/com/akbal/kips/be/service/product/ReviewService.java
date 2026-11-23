package com.akbal.kips.be.service.product;

import com.akbal.kips.be.dto.product.ReviewDtos.Review;
import com.akbal.kips.be.dto.product.ReviewDtos.ReviewRequest;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.service.staff.StaffAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final String REVIEW_FIELDS = """
            r.id, r.product_id, u.username, r.rating, r.comment, r.created_at
            """;

    private final JdbcClient jdbc;
    private final StaffAudit audit;

    @Transactional(readOnly = true)
    public Page<Review> getReviews(long productId, Pageable pageable) {
        var total = jdbc.sql("SELECT review_count FROM product WHERE id = :id").param("id", productId)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        var reviews = jdbc.sql("SELECT " + REVIEW_FIELDS + """
                FROM product_review r JOIN app_user u ON u.id = r.user_id
                WHERE r.product_id = :product ORDER BY r.id DESC LIMIT :size OFFSET :offset
                """).param("product", productId).param("size", pageable.getPageSize())
                .param("offset", pageable.getOffset()).query(Review.class).list();
        return new PageImpl<>(reviews, pageable, total);
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.SHOP_OWN + "') and #username == authentication.name")
    public Review addReview(String username, long productId, ReviewRequest request) {
        var userId = jdbc.sql("SELECT id FROM app_user WHERE username = :username").param("username", username)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        requireProduct(productId, "NO KEY UPDATE");
        var purchased = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM shop_order o JOIN shop_order_item i ON i.order_id = o.id
                    WHERE o.user_id = :user AND i.product_id = :product AND o.status IN ('PAID', 'SHIPPED'))
                """).param("user", userId).param("product", productId).query(Boolean.class).single();
        if (!purchased) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers who bought the product can review it");
        }
        var comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        var reviewId = jdbc.sql("""
                INSERT INTO product_review(product_id, user_id, rating, comment) VALUES (:product, :user, :rating, :comment)
                ON CONFLICT (product_id, user_id) DO NOTHING RETURNING id
                """).param("product", productId).param("user", userId).param("rating", request.rating())
                .param("comment", comment).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Product already reviewed"));
        jdbc.sql("""
                UPDATE product SET review_count = review_count + 1, rating_total = rating_total + :rating
                WHERE id = :product
                """).param("rating", request.rating()).param("product", productId).update();
        return jdbc.sql("SELECT " + REVIEW_FIELDS + """
                FROM product_review r JOIN app_user u ON u.id = r.user_id WHERE r.id = :id
                """).param("id", reviewId).query(Review.class).single();
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_EDIT + "')")
    public void deleteReview(long productId, long reviewId) {
        requireProduct(productId, "NO KEY UPDATE");
        var removed = jdbc.sql("""
                DELETE FROM product_review r USING app_user u
                WHERE r.id = :id AND r.product_id = :product AND u.id = r.user_id
                RETURNING r.id, r.product_id, u.username, r.rating, r.comment, r.created_at
                """)
                .param("id", reviewId).param("product", productId).query(Review.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        jdbc.sql("""
                UPDATE product SET review_count = review_count - 1, rating_total = rating_total - :rating
                WHERE id = :product
                """).param("rating", removed.rating()).param("product", productId).update();
        audit.record("REVIEW_DELETED", "REVIEW", reviewId, removed, Map.of("deleted", true), null);
    }

    private void requireProduct(long productId, String lock) {
        jdbc.sql("SELECT id FROM product WHERE id = :id FOR " + lock).param("id", productId)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
