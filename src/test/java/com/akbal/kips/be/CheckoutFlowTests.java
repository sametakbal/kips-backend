package com.akbal.kips.be;

import com.akbal.kips.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutFlowTests extends IntegrationTestSupport {

    @Test
    void validatesCheckoutInputBeforeChangingAnything() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var other = register(false);
        var productId = createProduct(admin, 5);
        var address = addressId(customer);
        var foreignAddress = addressId(other);

        request("POST", CHECKOUT, Map.of("addressId", address), customer.accessToken(), 400);
        var noBody = baseRequest(CHECKOUT, customer.accessToken()).header("Idempotency-Key", "no-body")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        send(noBody, 400);
        var missingAddress = baseRequest(CHECKOUT, customer.accessToken()).header("Idempotency-Key", "no-address")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        send(missingAddress, 400);
        checkout(customer, "k".repeat(129), address, 400);
        checkout(customer, "empty-cart", address, 409);

        addToCart(customer, productId, 1);
        checkout(customer, "foreign-address", foreignAddress, 404);
        checkout(customer, "missing-address", Long.MAX_VALUE, 404);
        assertThat(stock(productId, admin)).isEqualTo(5);
        assertThat(cartSize(customer)).isEqualTo(1);
        assertThat(orderCount(customer)).isZero();
    }

    @Test
    void rejectsOversoldCartsWithoutLosingTheCartOrStock() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 1);

        addToCart(customer, productId, 2);
        checkout(customer, "oversold", 409);

        assertThat(stock(productId, admin)).isEqualTo(1);
        assertThat(cartSize(customer)).isEqualTo(1);
        assertThat(orderCount(customer)).isZero();

        addToCart(customer, productId, 1);
        checkout(customer, "fits-now", 200);
        assertThat(stock(productId, admin)).isZero();
        checkout(customer, "after-sellout", 409);
    }

    @Test
    void scopesIdempotencyKeysToTheAccountAndChargesStockOnce() throws Exception {
        var admin = register(true);
        var first = register(false);
        var second = register(false);
        var productId = createProduct(admin, 10);
        addToCart(first, productId, 2);
        addToCart(second, productId, 3);

        var firstOrder = checkout(first, "shared-key", 200);
        var secondOrder = checkout(second, "shared-key", 200);

        assertThat(firstOrder.get("summary").get("id").asLong())
                .isNotEqualTo(secondOrder.get("summary").get("id").asLong());
        assertThat(checkout(first, "shared-key", 200)).isEqualTo(firstOrder);
        assertThat(checkout(second, "shared-key", 200)).isEqualTo(secondOrder);
        assertThat(stock(productId, admin)).isEqualTo(5);
        assertThat(orderCount(first)).isEqualTo(1);
        assertThat(orderCount(second)).isEqualTo(1);
    }

    @Test
    void sellsTheLastUnitToExactlyOneConcurrentBuyer() throws Exception {
        var admin = register(true);
        var productId = createProduct(admin, 1);
        var buyers = new LinkedHashMap<Account, Long>();
        for (int i = 0; i < 4; i++) {
            var buyer = register(false);
            buyers.put(buyer, addressId(buyer));
            addToCart(buyer, productId, 1);
        }

        var outcomes = new ArrayList<Integer>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = buyers.entrySet().stream().map(buyer -> executor.submit(() -> {
                var request = baseRequest(CHECKOUT, buyer.getKey().accessToken()).header("Idempotency-Key", "race")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                                Map.of("addressId", buyer.getValue())))).build();
                return exchange(request).statusCode();
            })).toList();
            for (var future : futures) {
                outcomes.add(future.get());
            }
        }

        assertThat(outcomes).containsOnly(200, 409);
        assertThat(outcomes.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(stock(productId, admin)).isZero();
    }

    @Test
    void keepsPriceAndShippingSnapshotsAfterCatalogAndAddressChanges() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 5);
        var address = addressId(customer);
        addToCart(customer, productId, 2);
        var order = checkout(customer, "snapshot", address, 200);
        long orderId = order.get("summary").get("id").asLong();
        assertThat(order.get("summary").get("total").decimalValue()).isEqualByComparingTo("19.90");
        assertThat(order.get("summary").get("shippingCountry").asString()).isEqualTo("TR");

        request("PUT", PRODUCTS + "/" + productId,
                Map.of("name", "Repriced", "price", 99.5, "categoryId", 2), admin.accessToken(), 200);
        request("PUT", ADDRESSES + "/" + address, address("Moved", true), customer.accessToken(), 200);
        request("DELETE", ADDRESSES + "/" + address, null, customer.accessToken(), 200);

        var current = node(request("GET", ORDERS + "/" + orderId, null, customer.accessToken(), 200)).get("data");
        assertThat(current.get("summary").get("total").decimalValue()).isEqualByComparingTo("19.90");
        assertThat(current.get("summary").get("shippingLine1").asString()).isEqualTo("1 Test Street");
        assertThat(current.get("summary").get("shippingCity").asString()).isEqualTo("Istanbul");
        assertThat(current.get("items").get(0).get("productName").asString()).isEqualTo("Integration product");
        assertThat(current.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("9.95");
        addToCart(customer, productId, 1);
        assertThat(node(request("GET", CART, null, customer.accessToken(), 200)).get("data").get("total")
                .decimalValue()).isEqualByComparingTo("99.50");
    }

    @Test
    void movesOrdersThroughCancellationAndShippingWithoutInvalidTransitions() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 6);

        addToCart(customer, productId, 2);
        var cancelledId = checkout(customer, "to-cancel", 200).get("summary").get("id").asLong();
        assertThat(stock(productId, admin)).isEqualTo(4);
        var cancelPath = ORDERS + "/" + cancelledId + "/cancel";
        assertThat(status(request("POST", cancelPath, null, customer.accessToken(), 200))).isEqualTo("CANCELLED");
        assertThat(status(request("POST", cancelPath, null, customer.accessToken(), 200))).isEqualTo("CANCELLED");
        assertThat(stock(productId, admin)).isEqualTo(6);
        request("POST", "/api/v1/admin/orders/" + cancelledId + "/ship", null, admin.accessToken(), 409);

        addToCart(customer, productId, 1);
        var shippedId = checkout(customer, "to-ship", 200).get("summary").get("id").asLong();
        var shipPath = "/api/v1/admin/orders/" + shippedId + "/ship";
        request("POST", shipPath, null, customer.accessToken(), 403);
        assertThat(status(request("POST", shipPath, null, admin.accessToken(), 200))).isEqualTo("SHIPPED");
        assertThat(status(request("POST", shipPath, null, admin.accessToken(), 200))).isEqualTo("SHIPPED");
        request("POST", ORDERS + "/" + shippedId + "/cancel", null, customer.accessToken(), 409);
        assertThat(stock(productId, admin)).isEqualTo(5);
        request("POST", "/api/v1/admin/orders/" + Long.MAX_VALUE + "/ship", null, admin.accessToken(), 404);
    }

    @Test
    void validatesCartChanges() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 3);

        request("PUT", CART + "/items/" + productId, Map.of("quantity", 0), customer.accessToken(), 400);
        request("PUT", CART + "/items/" + productId, Map.of("quantity", -1), customer.accessToken(), 400);
        request("PUT", CART + "/items/" + Long.MAX_VALUE, Map.of("quantity", 1), customer.accessToken(), 404);
        addToCart(customer, productId, 2);
        addToCart(customer, productId, 3);
        var cart = node(request("GET", CART, null, customer.accessToken(), 200)).get("data");
        assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        request("DELETE", CART + "/items/" + productId, null, customer.accessToken(), 200);
        assertThat(cartSize(customer)).isZero();
    }

    private String status(HttpResponse<String> response) {
        return node(response).get("data").get("summary").get("status").asString();
    }

    private int cartSize(Account account) throws Exception {
        return node(request("GET", CART, null, account.accessToken(), 200)).get("data").get("items").size();
    }

    private int orderCount(Account account) throws Exception {
        return node(request("GET", ORDERS, null, account.accessToken(), 200)).get("data").size();
    }
}
