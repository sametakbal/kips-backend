package com.akbal.kips.be;

import com.akbal.kips.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlTests extends IntegrationTestSupport {
    private static final String[] CUSTOMER_READS = {CART, ORDERS, ADDRESSES, "/api/v1/wishlist",
            "/api/v1/user/profile", PRODUCTS};

    @Test
    void rejectsRequestsWithoutAValidAccessToken() throws Exception {
        for (var path : CUSTOMER_READS) {
            request("GET", path, null, null, 401);
            request("GET", path, null, "not-a-token", 401);
        }
        request("POST", CHECKOUT, Map.of("addressId", 1), null, 401);
        request("PUT", CART + "/items/1", Map.of("quantity", 1), null, 401);
        request("POST", PRODUCTS + "/1/reviews", Map.of("rating", 5), null, 401);
        request("POST", "/api/v1/auth/authenticate", Map.of("username", "nobody-here", "password", "bad-password"),
                null, 401);
    }

    @Test
    void keepsAdministrationAndCatalogWritesAwayFromCustomers() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 1);

        request("POST", PRODUCTS, product("Denied", 1), customer.accessToken(), 403);
        request("PUT", PRODUCTS + "/" + productId, product("Denied", 1), customer.accessToken(), 403);
        request("DELETE", PRODUCTS + "/" + productId, null, customer.accessToken(), 403);
        request("PUT", "/api/v1/admin/staff-roles?username=" + customer.username() + "&role=ROLE_ADMIN",
                null, customer.accessToken(), 403);
        request("POST", "/api/v1/staff/inventory/products/" + productId + "/adjust",
                Map.of("delta", 1, "reason", "Denied"), customer.accessToken(), 403);
        request("GET", "/api/v1/staff/fulfillment/orders", null, customer.accessToken(), 403);
        assertThat(stock(productId, admin)).isEqualTo(1);
    }

    @Test
    void hidesOrdersAndCartsOwnedBySomeoneElse() throws Exception {
        var admin = register(true);
        var owner = register(false);
        var intruder = register(false);
        var productId = createProduct(admin, 5);
        addToCart(owner, productId, 2);
        var orderId = checkout(owner, "private-order", 200).get("summary").get("id").asLong();

        request("GET", ORDERS + "/" + orderId, null, intruder.accessToken(), 404);
        request("POST", ORDERS + "/" + orderId + "/cancel", null, intruder.accessToken(), 404);
        assertThat(node(request("GET", ORDERS, null, intruder.accessToken(), 200)).get("data").size()).isZero();
        assertThat(node(request("GET", CART, null, intruder.accessToken(), 200)).get("data").get("items").size())
                .isZero();
        assertThat(node(request("GET", ORDERS + "/" + orderId, null, owner.accessToken(), 200))
                .get("data").get("summary").get("status").asString()).isEqualTo("PAID");
        assertThat(stock(productId, admin)).isEqualTo(3);
    }

    @Test
    void usesRefreshTokensOnlyToRefresh() throws Exception {
        var customer = register(false);

        request("GET", CART, null, customer.refreshToken(), 401);
        request("POST", "/api/v1/auth/refresh", Map.of("refresh_token", customer.accessToken()), null, 401);
        request("POST", "/api/v1/auth/refresh", Map.of("refresh_token", customer.refreshToken()), null, 200);
    }
}
