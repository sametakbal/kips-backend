package com.akbal.kips.be;

import com.akbal.kips.be.service.product.ProductCategoryService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"checkout.local-test-enabled=true", "spring.data.redis.database=15"})
class KipsBeApplicationTests {
    private static final String PRODUCTS = "/api/v1/products";
    private static final String CATEGORIES = "/api/v1/product-categories";
    private static final String CHECKOUT = "/api/v1/orders/checkout";
    private static final String STAFF_ROLES = "/api/v1/admin/staff-roles";
    private static final String FULFILLMENT_ORDERS = "/api/v1/staff/fulfillment/orders";
    private static final String SUPPORT_ORDERS = "/api/v1/staff/support/orders";
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    ProductCategoryService categories;

    @Test
    void separatesTokenTypesAndRejectsUnauthorizedCatalogWrites() throws Exception {
        var account = register(false);
        request("GET", PRODUCTS, null, null, 401);
        request("GET", PRODUCTS, null, account.refreshToken(), 401);
        request("GET", PRODUCTS, null, "invalid", 401);
        request("POST", PRODUCTS, product("Forbidden", 1), account.accessToken(), 403);
        request("POST", "/api/v1/auth/refresh", Map.of("refresh_token", account.accessToken()), null, 401);
        var refreshed = request("POST", "/api/v1/auth/refresh",
                Map.of("refresh_token", account.refreshToken()), null, 200);
        request("GET", PRODUCTS, null, node(refreshed).get("access_token").asString(), 200);
        request("POST", "/api/v1/auth/register",
                Map.of("username", account.username(), "email", account.username() + "@example.test",
                        "password", "integration-password"), null, 409);
    }

    @Test
    void validatesCatalogWritesAndInvalidatesTheCategoryCache() throws Exception {
        var admin = register(true);
        redis.delete("all_categories::SimpleKey []");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            var before = statistics.getPrepareStatementCount();
            assertThat(categories.getAllCategories()).isNotEmpty();
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        var cached = request("GET", CATEGORIES, null, admin.accessToken(), 200);
        assertThat(redis.getExpire("all_categories::SimpleKey []")).isPositive();
        var created = request("POST", CATEGORIES, Map.of("categories",
                new Object[]{Map.of("name", "Test " + UUID.randomUUID().toString().substring(0, 8),
                        "subCategories", new Object[]{Map.of("name", "Child category")})}), admin.accessToken(), 201);
        var category = node(created).get("data").get(0);
        assertThat(category.get("id").asLong()).isGreaterThan(1000);
        assertThat(category.get("children").get(0).get("name").asString()).isEqualTo("Child category");

        var current = request("GET", CATEGORIES, null, admin.accessToken(), 200);
        assertThat(node(current).get("data").size()).isEqualTo(node(cached).get("data").size() + 1);
        assertThat(node(request("GET", CATEGORIES, null, admin.accessToken(), 200))).isEqualTo(node(current));
        assertThat(node(request("POST", PRODUCTS, Map.of("name", "Missing fields"), admin.accessToken(), 400))
                .get("status").asString()).isEqualTo("400 BAD_REQUEST");
        request("POST", CATEGORIES, Map.of("categories", new Object[]{Map.of("name", "Parent",
                        "subCategories", new Object[]{Map.of("name", "")})}), admin.accessToken(), 400);
        request("GET", PRODUCTS + "/9223372036854775807", null, admin.accessToken(), 404);
        var productId = createProduct(admin, 3);
        var listed = request("GET", PRODUCTS + "?page=0&size=1&sort=id,desc&categoryId=2",
                null, admin.accessToken(), 200);
        assertThat(listed.headers().firstValue("X-Total-Count")).isPresent();
        assertThat(node(listed).get("data").get(0).get("id").asLong()).isEqualTo(productId);
        request("PUT", PRODUCTS + "/" + productId, product("Updated product", 2), admin.accessToken(), 200);
        request("DELETE", PRODUCTS + "/" + productId, null, admin.accessToken(), 200);
    }

    @Test
    void checksOutOnceAndRestoresStockWhenCancelled() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var unrelated = register(false);
        request("POST", PRODUCTS, Map.of("name", "No initial stock", "price", 9.95, "categoryId", 2),
                admin.accessToken(), 400);
        var productId = createProduct(admin, 3);

        request("PUT", "/api/v1/cart/items/" + productId, Map.of("quantity", 4), customer.accessToken(), 200);
        checkout(customer, "insufficient", 409);
        assertThat(stock(productId, admin)).isEqualTo(3);
        request("PUT", "/api/v1/cart/items/" + productId, Map.of("quantity", 2), customer.accessToken(), 200);
        JsonNode order;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> checkout(customer, "retry-key", 200));
            var second = executor.submit(() -> checkout(customer, "retry-key", 200));
            order = first.get();
            assertThat(second.get()).isEqualTo(order);
        }
        assertThat(order.get("summary").get("paymentMethod").asString()).isEqualTo("LOCAL_TEST");
        assertThat(order.get("summary").get("total").decimalValue()).isEqualByComparingTo("19.90");
        assertThat(checkout(customer, "retry-key", 200)).isEqualTo(order);
        assertThat(stock(productId, admin)).isEqualTo(1);
        request("PUT", PRODUCTS + "/" + productId, product("Stale catalog edit", 3), admin.accessToken(), 200);
        request("PUT", PRODUCTS + "/" + productId, Map.of("name", "Catalog edit", "price", 9.95, "categoryId", 2),
                admin.accessToken(), 200);
        assertThat(stock(productId, admin)).isEqualTo(1);
        assertThat(node(request("GET", "/api/v1/cart", null, customer.accessToken(), 200))
                .get("data").get("items").size()).isZero();

        var orderPath = "/api/v1/orders/" + order.get("summary").get("id").asLong();
        request("GET", orderPath, null, unrelated.accessToken(), 404);
        request("POST", orderPath + "/cancel", null, customer.accessToken(), 200);
        request("POST", orderPath + "/cancel", null, customer.accessToken(), 200);
        assertThat(stock(productId, admin)).isEqualTo(3);
    }

    @Test
    void retriesStockAdjustmentsWithoutApplyingTheDeltaOrAuditTwice() throws Exception {
        var admin = register(true);
        var id = createProduct(admin, 5);
        var adjustment = Map.of("delta", -2, "reason", "Damaged units");
        request("POST", stockPath(id), adjustment, admin.accessToken(), 400);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> adjustStock(id, admin, "damage", adjustment, 200));
            var second = executor.submit(() -> adjustStock(id, admin, "damage", adjustment, 200));
            assertThat(first.get().get("stock").asInt()).isEqualTo(3);
            assertThat(second.get().get("stock").asInt()).isEqualTo(3);
        }
        adjustStock(id, admin, "damage", Map.of("delta", -1, "reason", "Damaged units"), 409);
        adjustStock(id, admin, "damage", Map.of("delta", -2, "reason", "Different reason"), 409);
        adjustStock(id, admin, "recount", Map.of("delta", 1, "reason", "Recount"), 200);
        assertThat(adjustStock(id, admin, "damage", adjustment, 200).get("stock").asInt()).isEqualTo(3);
        assertThat(stock(id, admin)).isEqualTo(4);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM staff_audit_event
                WHERE action = 'STOCK_ADJUSTED' AND target_type = 'PRODUCT' AND target_id = :id
                """).param("id", id).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void publishesOpenApi() throws Exception {
        var document = node(request("GET", "/v3/api-docs", null, null, 200));
        assertThat(document.get("paths").has(CHECKOUT)).isTrue();
        assertThat(document.get("components").get("securitySchemes").has("bearerAuth")).isTrue();
    }

    @Test
    void managesProductFamiliesAndSearchesSellableVariantsWithoutChangingStockOrSku() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var families = "/api/v1/admin/product-families";
        var code = "shirt-" + UUID.randomUUID().toString().substring(0, 8);
        var familyBody = Map.of("code", code, "name", "Linen shirt");
        request("POST", families, familyBody, customer.accessToken(), 403);
        var family = node(request("POST", families, familyBody, admin.accessToken(), 201)).get("data");
        long familyId = family.get("id").asLong();
        request("POST", families, familyBody, admin.accessToken(), 409);
        assertThat(node(request("GET", "/api/v1/product-families/" + familyId, null, customer.accessToken(), 200))
                .get("data").get("code").asString()).isEqualTo(code.toUpperCase(java.util.Locale.ROOT));

        var sku = code + "-S";
        var details = Map.of("brand", "Kips", "countryOfOrigin", "TR", "condition", "NEW",
                "variantOptions", Map.of("size", "S", "color", "Blue"), "vatRate", 20,
                "attributes", Map.of("material", "linen"), "imageUrls", List.of("https://example.test/shirt.jpg"),
                "packageDimensions", Map.of("lengthMm", 280, "weightGrams", 250));
        var metadata = Map.of("familyId", familyId, "gtin", "4006381333931", "details", details);
        var variant = Map.of("name", "Linen shirt blue S", "price", 9.95, "stock", 7,
                "categoryId", 2, "sku", sku, "metadata", metadata);
        var created = node(request("POST", PRODUCTS, variant, admin.accessToken(), 200)).get("data");
        long id = created.get("id").asLong();
        assertThat(created.get("sku").asString()).isEqualTo(sku.toUpperCase(java.util.Locale.ROOT));
        request("POST", PRODUCTS, variant, admin.accessToken(), 409);

        var medium = Map.of("name", "Linen shirt blue M", "price", 9.95, "stock", 8,
                "categoryId", 2, "sku", code + "-M", "metadata", Map.of("familyId", familyId, "details",
                        Map.of("variantOptions", Map.of("size", "M", "color", "Blue"))));
        request("POST", PRODUCTS, medium, admin.accessToken(), 200);
        request("POST", PRODUCTS, Map.of("name", "Duplicate selection", "price", 9.95, "stock", 1,
                "categoryId", 2, "metadata", Map.of("familyId", familyId, "details",
                        Map.of("variantOptions", Map.of("color", "Blue", "size", "S")))), admin.accessToken(), 409);
        request("POST", PRODUCTS, Map.of("name", "Duplicate GTIN", "price", 9.95, "stock", 1,
                "categoryId", 2, "metadata", Map.of("gtin", "04006381333931")), admin.accessToken(), 409);

        var matches = node(request("GET", PRODUCTS + "?familyId=" + familyId + "&categoryId=2&q=SHIRT&sort=sku,asc",
                null, customer.accessToken(), 200)).get("data");
        assertThat(matches.size()).isEqualTo(2);
        var exact = node(request("GET", PRODUCTS + "?sku=" + sku + "&gtin=04006381333931",
                null, customer.accessToken(), 200)).get("data");
        assertThat(exact.size()).isEqualTo(1);
        assertThat(exact.get(0).get("metadata").get("details").get("brand").asString()).isEqualTo("Kips");

        var metadataPath = "/api/v1/admin/products/" + id + "/metadata";
        request("PUT", metadataPath, Map.of("gtin", "4006381333932"), admin.accessToken(), 400);
        request("PUT", metadataPath, Map.of("details", Map.of("imageUrls", List.of("file:///a.jpg"))),
                admin.accessToken(), 400);
        request("PUT", metadataPath, Map.of("familyId", Long.MAX_VALUE), admin.accessToken(), 404);
        request("PUT", metadataPath, metadata, admin.accessToken(), 200);
        assertThat(stock(id, admin)).isEqualTo(7);
        var updated = node(request("PUT", PRODUCTS + "/" + id, product("Legacy edit", 7), admin.accessToken(), 200))
                .get("data");
        assertThat(updated.get("metadata").get("gtin").asString()).isEqualTo("4006381333931");
        request("PUT", PRODUCTS + "/" + id, Map.of("name", "Renamed", "price", 9.95, "stock", 7,
                "categoryId", 2, "sku", "different-sku"), admin.accessToken(), 409);
        request("DELETE", families + "/" + familyId, null, admin.accessToken(), 409);
    }

    @Test
    void managesMarketplaceListingsByProductChannelSellerAndMarket() throws Exception {
        var admin = register(true);
        var customer = register(false);
        long id = createProduct(admin, 4);
        long otherId = createProduct(admin, 6);
        var base = "/api/v1/admin/products/" + id + "/marketplace-listings";
        var sku = "market-" + UUID.randomUUID().toString().substring(0, 8);
        var body = Map.of("channelSku", sku, "externalCatalogId", "external-catalog-id",
                "categoryReference", "123", "productType", "SHIRT", "currency", "TRY",
                "salePrice", 90, "listPrice", 110, "channelAttributes", Map.of("values", List.of(11, 22)));
        var amazon = base + "/AMAZON?account=retail&market=tr";
        request("GET", base, null, customer.accessToken(), 403);
        request("PUT", amazon, body, customer.accessToken(), 403);
        var created = node(request("PUT", amazon, body, admin.accessToken(), 200)).get("data");
        var retry = node(request("PUT", base + "/AMAZON?account=RETAIL&market=TR", body, admin.accessToken(), 200))
                .get("data");
        assertThat(retry).isEqualTo(created);
        for (var channel : List.of("HEPSIBURADA", "TRENDYOL", "N11")) {
            request("PUT", base + "/" + channel + "?account=retail&market=tr", body, admin.accessToken(), 200);
        }
        var hepsiburada = base + "/HEPSIBURADA?account=retail&market=tr";
        assertThat(node(request("PUT", hepsiburada, body, admin.accessToken(), 200)).get("data")
                .get("channelSku").asString()).isEqualTo(sku.toUpperCase(java.util.Locale.ROOT));
        request("PUT", "/api/v1/admin/products/" + otherId + "/marketplace-listings/HEPSIBURADA?account=retail&market=tr",
                Map.of("channelSku", sku.toUpperCase(java.util.Locale.ROOT)), admin.accessToken(), 409);
        request("PUT", hepsiburada, Map.of("channelSku", "SKU WITH SPACE"), admin.accessToken(), 400);
        request("PUT", "/api/v1/admin/products/" + otherId + "/marketplace-listings/AMAZON?account=retail&market=tr",
                body, admin.accessToken(), 409);
        request("PUT", base + "/AMAZON?account=retail&market=DE", body, admin.accessToken(), 200);
        request("PUT", amazon, Map.of("channelSku", sku, "salePrice", 40), admin.accessToken(), 400);
        request("PUT", amazon, Map.of("channelSku", sku, "salePrice", 40, "listPrice", 20, "currency", "TRY"),
                admin.accessToken(), 400);
        var listings = node(request("GET", base, null, admin.accessToken(), 200)).get("data");
        assertThat(listings.size()).isEqualTo(5);
        assertThat(listings.get(0).get("channelAttributes").get("values").get(1).asInt()).isEqualTo(22);
        request("DELETE", amazon, null, admin.accessToken(), 200);
        request("DELETE", amazon, null, admin.accessToken(), 200);
        assertThat(node(request("GET", base, null, admin.accessToken(), 200)).get("data").size()).isEqualTo(4);
        assertThat(stock(id, admin)).isEqualTo(4);
        assertThat(node(request("GET", PRODUCTS + "/" + id, null, customer.accessToken(), 200))
                .get("data").get("price").decimalValue()).isEqualByComparingTo("9.95");
    }

    @Test
    void validatesAndPersistsMarketplacePricesAtCurrencyPrecision() throws Exception {
        var admin = register(true);
        var id = createProduct(admin, 2);
        var base = "/api/v1/admin/products/" + id + "/marketplace-listings";
        var listing = base + "/AMAZON?account=retail&market=jp";
        request("PUT", listing, Map.of("channelSku", "precision-" + id, "currency", "JPY", "salePrice", 100.50),
                admin.accessToken(), 400);
        request("PUT", listing, Map.of("channelSku", "precision-" + id, "currency", "JPY", "salePrice", 100),
                admin.accessToken(), 200);
        request("PUT", listing, Map.of("channelSku", "precision-" + id, "currency", "TRY", "salePrice", 100,
                "listPrice", 100.001), admin.accessToken(), 400);
        request("PUT", listing, Map.of("channelSku", "precision-" + id, "currency", "KWD", "salePrice", 100.125),
                admin.accessToken(), 200);
        assertThat(node(request("GET", base, null, admin.accessToken(), 200)).get("data").get(0)
                .get("salePrice").decimalValue()).isEqualByComparingTo("100.125");
    }

    @Test
    void grantsAndRevokesOnlyStaffRolesAndAuditsActualChanges() throws Exception {
        var admin = register(true);
        var employee = register(false);
        var target = STAFF_ROLES + "?username=" + employee.username();
        request("PUT", target + "&role=ROLE_CATALOG_STAFF", null, employee.accessToken(), 403);
        request("PUT", target + "&role=ROLE_ADMIN", null, admin.accessToken(), 400);
        request("DELETE", target + "&role=ROLE_USER", null, admin.accessToken(), 400);
        request("GET", STAFF_ROLES + "?username=missing-" + employee.username(), null, admin.accessToken(), 404);

        assign(admin, employee, "ROLE_CATALOG_STAFF");
        assign(admin, employee, "ROLE_CATALOG_STAFF");
        assign(admin, employee, "ROLE_FULFILLMENT_STAFF");
        long productId = createProduct(admin, 2);
        var content = Map.of("name", "New title", "categoryId", 2);
        request("PATCH", contentPath(productId), content, employee.accessToken(), 200);

        var revoked = node(request("DELETE", target + "&role=ROLE_CATALOG_STAFF", null, admin.accessToken(), 200));
        assertThat(revoked.get("data").get("roles").toString())
                .contains("ROLE_USER", "ROLE_FULFILLMENT_STAFF").doesNotContain("ROLE_CATALOG_STAFF");
        request("DELETE", target + "&role=ROLE_CATALOG_STAFF", null, admin.accessToken(), 200);
        request("PATCH", contentPath(productId), content, employee.accessToken(), 403);
        adjustStock(productId, employee, "count", Map.of("delta", 1, "reason", "Counted one more"), 200);
        request("GET", "/api/v1/cart", null, employee.accessToken(), 200);

        var events = jdbc.sql("""
                SELECT e.previous_value::text || ' -> ' || e.new_value::text
                FROM staff_audit_event e JOIN app_user u ON u.id = e.target_id
                WHERE e.actor_username = :actor AND u.username = :target AND e.action = 'STAFF_ROLES_CHANGED'
                ORDER BY e.id
                """).param("actor", admin.username()).param("target", employee.username()).query(String.class).list();
        assertThat(events).hasSize(3);
        assertThat(events.getFirst()).startsWith("[\"ROLE_USER\"] -> ").contains("ROLE_CATALOG_STAFF");
        assertThat(events.getLast().split(" -> ")[1]).contains("ROLE_USER", "ROLE_FULFILLMENT_STAFF")
                .doesNotContain("ROLE_CATALOG_STAFF");
    }

    @Test
    void restrictsCatalogAndInventoryWritesAndRecordsStockAdjustments() throws Exception {
        var admin = register(true);
        var catalog = register(false);
        var warehouse = register(false);
        assign(admin, catalog, "ROLE_CATALOG_STAFF");
        assign(admin, warehouse, "ROLE_FULFILLMENT_STAFF");
        long id = createProduct(admin, 5);

        var content = Map.of("name", "Editorial name", "description", "Description", "categoryId", 2);
        var edited = node(request("PATCH", contentPath(id), content, catalog.accessToken(), 200)).get("data");
        assertThat(edited.get("name").asString()).isEqualTo("Editorial name");
        assertThat(edited.get("stock").asInt()).isEqualTo(5);
        assertThat(edited.get("price").decimalValue()).isEqualByComparingTo("9.95");
        request("POST", CATEGORIES, Map.of("categories", new Object[]{Map.of("name", "Staff category")}),
                catalog.accessToken(), 201);
        request("PATCH", contentPath(id), Map.of("name", "Edit", "categoryId", 2, "price", 1, "stock", 99),
                catalog.accessToken(), 400);
        request("PUT", PRODUCTS + "/" + id, product("Cannot save", 1), catalog.accessToken(), 403);
        request("DELETE", PRODUCTS + "/" + id, null, catalog.accessToken(), 403);
        request("PATCH", contentPath(id), content, warehouse.accessToken(), 403);
        request("PUT", PRODUCTS + "/" + id, product("Cannot save", 1), warehouse.accessToken(), 403);
        request("POST", stockPath(id), Map.of("delta", 1, "reason", "Catalog denied"), catalog.accessToken(), 403);
        adjustStock(id, warehouse, "missing-reason", Map.of("delta", 1), 400);
        adjustStock(id, warehouse, "invalid", Map.of("delta", -6, "reason", "Invalid count"), 409);
        adjustStock(id, warehouse, "damage", Map.of("delta", -2, "reason", "Damaged units"), 200);
        assertThat(stock(id, admin)).isEqualTo(3);

        var audit = jdbc.sql("""
                SELECT previous_value::text || ':' || new_value::text || ':' || reason
                FROM staff_audit_event WHERE action = 'STOCK_ADJUSTED' AND target_type = 'PRODUCT'
                    AND target_id = :id AND actor_username = :actor
                """).param("id", id).param("actor", warehouse.username()).query(String.class).list();
        assertThat(audit).containsExactly("5:3:Damaged units");

        request("PUT", "/api/v1/cart/items/" + id, Map.of("quantity", 2), catalog.accessToken(), 200);
        var warehouseCart = node(request("GET", "/api/v1/cart", null, warehouse.accessToken(), 200));
        assertThat(warehouseCart.get("data").get("items").size()).isZero();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var adjustment = executor.submit(() -> adjustStock(id, warehouse, "found",
                    Map.of("delta", 2, "reason", "Found two units"), 200));
            var order = executor.submit(() -> checkout(catalog, "concurrent-adjustment", 200));
            adjustment.get();
            order.get();
        }
        assertThat(stock(id, admin)).isEqualTo(3);
    }

    @Test
    void separatesFulfillmentFromSupportAndKeepsOrderOwnership() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var warehouse = register(false);
        var support = register(false);
        assign(admin, warehouse, "ROLE_FULFILLMENT_STAFF");
        assign(admin, support, "ROLE_SUPPORT_AGENT");

        long productId = createProduct(admin, 5);
        request("PUT", "/api/v1/cart/items/" + productId, Map.of("quantity", 2), customer.accessToken(), 200);
        var order = checkout(customer, "staff-test", 200);
        long orderId = order.get("summary").get("id").asLong();
        String ownOrder = "/api/v1/orders/" + orderId;
        String fulfillmentOrder = FULFILLMENT_ORDERS + "/" + orderId;
        String supportOrder = SUPPORT_ORDERS + "/" + orderId;

        request("GET", ownOrder, null, support.accessToken(), 404);
        request("GET", supportOrder, null, customer.accessToken(), 403);
        request("GET", supportOrder, null, warehouse.accessToken(), 403);
        request("GET", FULFILLMENT_ORDERS, null, support.accessToken(), 403);
        request("POST", fulfillmentOrder + "/ship", null, support.accessToken(), 403);
        request("POST", stockPath(productId), Map.of("delta", 1, "reason", "Denied"), support.accessToken(), 403);
        request("GET", FULFILLMENT_ORDERS, null, warehouse.accessToken(), 200);
        var packing = node(request("GET", fulfillmentOrder, null, warehouse.accessToken(), 200)).get("data");
        assertThat(packing.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(packing.get("items").get(0).has("unitPrice")).isFalse();
        assertThat(packing.get("summary").has("total")).isFalse();

        var limited = node(request("GET", supportOrder, null, support.accessToken(), 200)).get("data");
        assertThat(limited.get("maskedEmail").asString()).isEqualTo("t***@***");
        assertThat(limited.size()).isEqualTo(4);
        request("POST", "/api/v1/admin/orders/" + orderId + "/ship", null, warehouse.accessToken(), 403);
        request("POST", fulfillmentOrder + "/ship", null, warehouse.accessToken(), 200);
        request("POST", fulfillmentOrder + "/ship", null, warehouse.accessToken(), 200);
        request("POST", ownOrder + "/cancel", null, customer.accessToken(), 409);
        var current = node(request("GET", ownOrder, null, customer.accessToken(), 200)).get("data");
        assertThat(current.get("summary").get("status").asString()).isEqualTo("SHIPPED");

        var audit = jdbc.sql("""
                SELECT (previous_value #>> '{}') || ':' || (new_value #>> '{}')
                FROM staff_audit_event WHERE action = 'ORDER_SHIPPED' AND target_type = 'ORDER'
                    AND target_id = :id AND actor_username = :actor
                """).param("id", orderId).param("actor", warehouse.username()).query(String.class).list();
        assertThat(audit).containsExactly("PAID:SHIPPED");
    }

    @Test
    void updatesStaffPermissionsForExistingTokensWithoutChangingProtectedOrOtherRoles() throws Exception {
        var admin = register(true);
        var first = register(false);
        var second = register(false);
        assign(admin, first, "ROLE_SUPPORT_AGENT");
        assign(admin, second, "ROLE_SUPPORT_AGENT");
        assign(admin, second, "ROLE_CATALOG_STAFF");
        var catalog = STAFF_ROLES + "/permissions";
        var policy = STAFF_ROLES + "/ROLE_SUPPORT_AGENT/permissions";
        var supportOrder = SUPPORT_ORDERS + "/9223372036854775807";
        var productId = createProduct(admin, 2);
        var content = Map.of("name", "Managed permissions", "categoryId", 2);
        var defaults = Map.of("permissions", List.of("ORDER_READ_SUPPORT"));
        var requested = Map.of("permissions", List.of("CATALOG_EDIT"));

        var available = node(request("GET", catalog, null, admin.accessToken(), 200)).get("data");
        assertThat(available.get("roles").size()).isEqualTo(3);
        assertThat(available.get("permissions").size()).isEqualTo(5);
        request("GET", catalog, null, first.accessToken(), 403);
        request("PUT", policy, requested, first.accessToken(), 403);
        request("PUT", STAFF_ROLES + "/ROLE_ADMIN/permissions", requested, admin.accessToken(), 400);
        request("PUT", STAFF_ROLES + "/ROLE_USER/permissions", requested, admin.accessToken(), 400);
        for (var forbidden : List.of("SHOP_OWN", "CATALOG_MANAGE", "STAFF_ROLE_MANAGE")) {
            request("PUT", policy, Map.of("permissions", List.of("CATALOG_EDIT", forbidden)), admin.accessToken(), 400);
        }
        request("PUT", policy, Map.of("permissions", List.of(0)), admin.accessToken(), 400);
        request("PUT", policy, Map.of(), admin.accessToken(), 400);
        assertThat(node(request("GET", policy, null, admin.accessToken(), 200)).get("data").get("permissions"))
                .isEqualTo(json.valueToTree(defaults.get("permissions")));
        request("GET", supportOrder, null, first.accessToken(), 404);
        request("PATCH", contentPath(productId), content, first.accessToken(), 403);

        try {
            request("PUT", policy, requested, admin.accessToken(), 200);
            request("PUT", policy, requested, admin.accessToken(), 200);
            request("GET", supportOrder, null, first.accessToken(), 403);
            request("PATCH", contentPath(productId), content, first.accessToken(), 200);
            request("PATCH", contentPath(productId), content, second.accessToken(), 200);
            request("PUT", PRODUCTS + "/" + productId, product("Denied", 0), first.accessToken(), 403);

            request("PUT", policy, Map.of("permissions", List.of()), admin.accessToken(), 200);
            request("PATCH", contentPath(productId), content, first.accessToken(), 403);
            request("PATCH", contentPath(productId), content, second.accessToken(), 200);
            request("GET", "/api/v1/cart", null, first.accessToken(), 200);

            var audit = jdbc.sql("""
                    SELECT e.previous_value::text || ' -> ' || e.new_value::text
                    FROM staff_audit_event e JOIN app_user_roles r ON r.id = e.target_id
                    WHERE e.actor_username = :actor AND r.name = 'ROLE_SUPPORT_AGENT'
                        AND e.target_type = 'ROLE' AND e.action = 'STAFF_PERMISSIONS_CHANGED'
                    ORDER BY e.id
                    """).param("actor", admin.username()).query(String.class).list();
            assertThat(audit).containsExactly("[\"ORDER_READ_SUPPORT\"] -> [\"CATALOG_EDIT\"]",
                    "[\"CATALOG_EDIT\"] -> []");
        } finally {
            request("PUT", policy, defaults, admin.accessToken(), 200);
        }
    }

    private void assign(Account admin, Account employee, String role) throws Exception {
        request("PUT", STAFF_ROLES + "?username=" + employee.username() + "&role=" + role,
                null, admin.accessToken(), 200);
    }

    private String contentPath(long id) {
        return "/api/v1/staff/catalog/products/" + id + "/content";
    }

    private String stockPath(long id) {
        return "/api/v1/staff/inventory/products/" + id + "/adjust";
    }

    private JsonNode adjustStock(long id, Account actor, String key, Object payload, int status) throws Exception {
        var request = baseRequest(stockPath(id), actor.accessToken()).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        return node(send(request, status)).get("data");
    }

    private Account register(boolean admin) throws Exception {
        var username = "test-" + UUID.randomUUID().toString().substring(0, 12);
        var response = node(request("POST", "/api/v1/auth/register", Map.of("username", username,
                "email", username + "@example.test", "password", "integration-password"), null, 200));

        if (admin) {
            int assigned = jdbc.sql("""
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM app_user u CROSS JOIN app_user_roles r
                    WHERE u.username = :username AND r.name = 'ROLE_ADMIN'
                    """).param("username", username).update();
            assertThat(assigned).isEqualTo(1);
        }
        return new Account(username, response.get("access_token").asString(),
                response.get("refresh_token").asString());
    }

    private long createProduct(Account admin, int stock) throws Exception {
        return node(request("POST", PRODUCTS, product("Integration product", stock), admin.accessToken(), 200))
                .get("data").get("id").asLong();
    }

    private Map<String, Object> product(String name, int stock) {
        return Map.of("name", name, "price", 9.95, "stock", stock, "categoryId", 2);
    }

    private int stock(long id, Account admin) throws Exception {
        return node(request("GET", PRODUCTS + "/" + id, null, admin.accessToken(), 200))
                .get("data").get("stock").asInt();
    }

    private JsonNode checkout(Account account, String key, int status) throws Exception {
        var request = baseRequest(CHECKOUT, account.accessToken()).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        return node(send(request, status)).get("data");
    }

    private HttpResponse<String> request(String method, String path, Object payload, String token, int status)
            throws Exception {
        var body = payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload));
        var request = baseRequest(path, token).method(method, body).build();
        return send(request, status);
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request, int status) throws Exception {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(), response.body())
                .isEqualTo(status);
        return response;
    }

    private JsonNode node(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private record Account(String username, String accessToken, String refreshToken) {
    }
}
