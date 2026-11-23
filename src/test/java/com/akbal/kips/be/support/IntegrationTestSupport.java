package com.akbal.kips.be.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"checkout.local-test-enabled=true", "spring.data.redis.database=15"})
public abstract class IntegrationTestSupport {
    protected static final String PRODUCTS = "/api/v1/products";
    protected static final String CHECKOUT = "/api/v1/orders/checkout";
    protected static final String ADDRESSES = "/api/v1/addresses";
    protected static final String CART = "/api/v1/cart";
    protected static final String ORDERS = "/api/v1/orders";

    private final HttpClient http = HttpClient.newHttpClient();
    protected final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    protected Account register(boolean admin) throws Exception {
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

    protected long createProduct(Account admin, int stock) throws Exception {
        return node(request("POST", PRODUCTS, product("Integration product", stock), admin.accessToken(), 200))
                .get("data").get("id").asLong();
    }

    protected Map<String, Object> product(String name, int stock) {
        return Map.of("name", name, "price", 9.95, "stock", stock, "categoryId", 2);
    }

    protected int stock(long id, Account admin) throws Exception {
        return node(request("GET", PRODUCTS + "/" + id, null, admin.accessToken(), 200))
                .get("data").get("stock").asInt();
    }

    protected Map<String, Object> address(String label, boolean makeDefault) {
        return Map.of("label", label, "recipientName", "Test Customer", "phone", "+90 555 000 0000",
                "line1", "1 Test Street", "city", "Istanbul", "postalCode", "34000", "country", "tr",
                "defaultAddress", makeDefault);
    }

    protected long createAddress(Account account, boolean makeDefault) throws Exception {
        return node(request("POST", ADDRESSES, address("Home", makeDefault), account.accessToken(), 201))
                .get("data").get("id").asLong();
    }

    protected long addressId(Account account) throws Exception {
        var existing = node(request("GET", ADDRESSES, null, account.accessToken(), 200)).get("data");
        return existing.isEmpty() ? createAddress(account, true) : existing.get(0).get("id").asLong();
    }

    protected void addToCart(Account account, long productId, int quantity) throws Exception {
        request("PUT", CART + "/items/" + productId, Map.of("quantity", quantity), account.accessToken(), 200);
    }

    protected JsonNode checkout(Account account, String key, int status) throws Exception {
        return checkout(account, key, addressId(account), status);
    }

    protected JsonNode checkout(Account account, String key, long addressId, int status) throws Exception {
        var request = baseRequest(CHECKOUT, account.accessToken()).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("addressId", addressId))))
                .build();
        return node(send(request, status)).get("data");
    }

    protected HttpResponse<String> request(String method, String path, Object payload, String token, int status)
            throws Exception {
        var body = payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload));
        var request = baseRequest(path, token).method(method, body).build();
        return send(request, status);
    }

    protected HttpRequest.Builder baseRequest(String path, String token) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected HttpResponse<String> send(HttpRequest request, int status) throws Exception {
        var response = exchange(request);
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(), response.body())
                .isEqualTo(status);
        return response;
    }

    protected HttpResponse<String> exchange(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected JsonNode node(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    public record Account(String username, String accessToken, String refreshToken) {
    }
}
