package com.akbal.kips.be;

import com.akbal.kips.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotificationTests extends IntegrationTestSupport {

    @Test
    void queuesOneNotificationPerOrderStateChange() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 5);

        addToCart(customer, productId, 1);
        long shippedId = checkout(customer, "notify-ship", 200).get("summary").get("id").asLong();
        checkout(customer, "notify-ship", 200);
        assertThat(templates(customer, shippedId)).containsExactly("ORDER_CREATED");

        var shipPath = "/api/v1/admin/orders/" + shippedId + "/ship";
        request("POST", shipPath, null, admin.accessToken(), 200);
        request("POST", shipPath, null, admin.accessToken(), 200);
        assertThat(templates(customer, shippedId)).containsExactly("ORDER_CREATED", "ORDER_SHIPPED");

        addToCart(customer, productId, 1);
        long cancelledId = checkout(customer, "notify-cancel", 200).get("summary").get("id").asLong();
        request("POST", ORDERS + "/" + cancelledId + "/cancel", null, customer.accessToken(), 200);
        request("POST", ORDERS + "/" + cancelledId + "/cancel", null, customer.accessToken(), 200);
        assertThat(templates(customer, cancelledId)).containsExactly("ORDER_CREATED", "ORDER_CANCELLED");

        var recipient = jdbc.sql("""
                SELECT DISTINCT n.recipient FROM notification_outbox n JOIN app_user u ON u.id = n.user_id
                WHERE u.username = :username
                """).param("username", customer.username()).query(String.class).single();
        assertThat(recipient).isEqualTo(customer.username() + "@example.test");
    }

    @Test
    void doesNotQueueNotificationsForRejectedCheckouts() throws Exception {
        var admin = register(true);
        var customer = register(false);
        var productId = createProduct(admin, 1);

        addToCart(customer, productId, 2);
        checkout(customer, "rejected", 409);

        assertThat(jdbc.sql("""
                SELECT count(*) FROM notification_outbox n JOIN app_user u ON u.id = n.user_id
                WHERE u.username = :username
                """).param("username", customer.username()).query(Long.class).single()).isZero();
    }

    private List<String> templates(Account account, long orderId) {
        return jdbc.sql("""
                SELECT n.template FROM notification_outbox n JOIN app_user u ON u.id = n.user_id
                WHERE u.username = :username AND (n.payload ->> 'orderId')::bigint = :order
                ORDER BY n.id
                """).param("username", account.username()).param("order", orderId)
                .query(String.class).list();
    }
}
