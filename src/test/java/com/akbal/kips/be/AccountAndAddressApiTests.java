package com.akbal.kips.be;

import com.akbal.kips.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountAndAddressApiTests extends IntegrationTestSupport {

    @Test
    void keepsExactlyOneDefaultAddressAndNormalizesTheCountry() throws Exception {
        var customer = register(false);
        var first = createAddress(customer, false);
        var second = createAddress(customer, false);
        assertThat(defaults(customer)).containsExactly(first);

        var third = createAddress(customer, true);
        assertThat(defaults(customer)).containsExactly(third);

        request("POST", ADDRESSES + "/" + second + "/default", null, customer.accessToken(), 200);
        request("POST", ADDRESSES + "/" + second + "/default", null, customer.accessToken(), 200);
        assertThat(defaults(customer)).containsExactly(second);

        var updated = node(request("PUT", ADDRESSES + "/" + first, address("Office", true), customer.accessToken(), 200))
                .get("data");
        assertThat(updated.get("country").asString()).isEqualTo("TR");
        assertThat(defaults(customer)).containsExactly(first);
        request("PUT", ADDRESSES + "/" + first, address("Office", false), customer.accessToken(), 200);
        assertThat(defaults(customer)).containsExactly(first);

        request("DELETE", ADDRESSES + "/" + first, null, customer.accessToken(), 200);
        request("DELETE", ADDRESSES + "/" + first, null, customer.accessToken(), 404);
        assertThat(defaults(customer)).isEmpty();
    }

    @Test
    void validatesAndLimitsAddresses() throws Exception {
        var customer = register(false);
        request("POST", ADDRESSES, Map.of("label", "Incomplete"), customer.accessToken(), 400);
        request("POST", ADDRESSES, withField("country", "TUR"), customer.accessToken(), 400);
        request("POST", ADDRESSES, withField("phone", "abc"), customer.accessToken(), 400);
        request("POST", ADDRESSES, withField("line1", " "), customer.accessToken(), 400);

        for (int i = 0; i < 20; i++) {
            request("POST", ADDRESSES, address("Address " + i, false), customer.accessToken(), 201);
        }
        request("POST", ADDRESSES, address("One too many", false), customer.accessToken(), 409);
    }

    @Test
    void keepsAddressesPrivateToTheirOwner() throws Exception {
        var owner = register(false);
        var intruder = register(false);
        var address = createAddress(owner, true);
        var path = ADDRESSES + "/" + address;

        request("PUT", path, address("Hijacked", true), intruder.accessToken(), 404);
        request("DELETE", path, null, intruder.accessToken(), 404);
        request("POST", path + "/default", null, intruder.accessToken(), 404);
        assertThat(node(request("GET", ADDRESSES, null, intruder.accessToken(), 200)).get("data").size()).isZero();
        assertThat(node(request("GET", ADDRESSES, null, owner.accessToken(), 200)).get("data").get(0).get("label")
                .asString()).isEqualTo("Home");
    }

    @Test
    void readsAndUpdatesTheProfile() throws Exception {
        var customer = register(false);
        var other = register(false);

        var profile = node(request("GET", "/api/v1/user/profile", null, customer.accessToken(), 200)).get("data");
        assertThat(profile.get("username").asString()).isEqualTo(customer.username());
        assertThat(profile.get("roles").get(0).asString()).isEqualTo("ROLE_USER");

        request("PUT", "/api/v1/user/profile", Map.of("email", "not-an-email"), customer.accessToken(), 400);
        request("PUT", "/api/v1/user/profile", Map.of("email", other.username() + "@example.test"),
                customer.accessToken(), 409);
        var changed = customer.username() + "-new@example.test";
        var updated = node(request("PUT", "/api/v1/user/profile", Map.of("email", changed), customer.accessToken(), 200));
        assertThat(updated.get("data").get("email").asString()).isEqualTo(changed);
    }

    @Test
    void changesPasswordsOnlyWithTheCurrentOne() throws Exception {
        var customer = register(false);
        var password = "/api/v1/user/password";
        var login = "/api/v1/auth/authenticate";

        request("PUT", password, Map.of("currentPassword", "wrong-password", "newPassword", "another-password"),
                customer.accessToken(), 400);
        request("PUT", password, Map.of("currentPassword", "integration-password", "newPassword", "short"),
                customer.accessToken(), 400);
        request("PUT", password, Map.of("currentPassword", "integration-password",
                "newPassword", "ü".repeat(37)), customer.accessToken(), 400);
        request("POST", login, Map.of("username", customer.username(), "password", "integration-password"),
                null, 200);

        request("PUT", password, Map.of("currentPassword", "integration-password",
                "newPassword", "another-password"), customer.accessToken(), 200);

        request("POST", login, Map.of("username", customer.username(), "password", "integration-password"),
                null, 401);
        request("POST", login, Map.of("username", customer.username(), "password", "another-password"),
                null, 200);
    }

    private Map<String, Object> withField(String field, Object value) {
        var request = new HashMap<>(address("Home", false));
        request.put(field, value);
        return request;
    }

    private List<Long> defaults(Account account) throws Exception {
        var addresses = node(request("GET", ADDRESSES, null, account.accessToken(), 200)).get("data");
        var result = new ArrayList<Long>();
        addresses.forEach(item -> {
            if (item.get("defaultAddress").asBoolean()) {
                result.add(item.get("id").asLong());
            }
        });
        return result;
    }
}
