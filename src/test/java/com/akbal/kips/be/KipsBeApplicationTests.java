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
        assertThat(node(request("GET", "/api/v1/cart", null, customer.accessToken(), 200))
                .get("data").get("items").size()).isZero();

        var orderPath = "/api/v1/orders/" + order.get("summary").get("id").asLong();
        request("GET", orderPath, null, unrelated.accessToken(), 404);
        request("POST", orderPath + "/cancel", null, customer.accessToken(), 200);
        request("POST", orderPath + "/cancel", null, customer.accessToken(), 200);
        assertThat(stock(productId, admin)).isEqualTo(3);
    }

    @Test
    void publishesOpenApi() throws Exception {
        var document = node(request("GET", "/v3/api-docs", null, null, 200));
        assertThat(document.get("paths").has(CHECKOUT)).isTrue();
        assertThat(document.get("components").get("securitySchemes").has("bearerAuth")).isTrue();
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
