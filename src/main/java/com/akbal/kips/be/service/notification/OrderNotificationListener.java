package com.akbal.kips.be.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {
    private final JdbcClient jdbc;
    private final NotificationService notifications;

    record OrderRecipient(long userId, String email, String status, BigDecimal total, String currency) {
    }

    /**
     * Runs only after the order change is committed. Any failure is logged and swallowed so a
     * notification problem never undoes or fails checkout, shipping or cancellation.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderEvent(OrderEvent event) {
        try {
            var order = jdbc.sql("""
                    SELECT o.user_id, u.email, o.status, o.total, o.currency
                    FROM shop_order o JOIN app_user u ON u.id = o.user_id
                    WHERE o.id = :id
                    """).param("id", event.orderId()).query(OrderRecipient.class).optional();
            if (order.isEmpty()) {
                log.warn("Skipping {} notification: order {} not found", event.type(), event.orderId());
                return;
            }
            var o = order.get();
            var payload = new LinkedHashMap<String, Object>();
            payload.put("orderId", event.orderId());
            payload.put("status", o.status());
            payload.put("total", o.total());
            payload.put("currency", o.currency());
            notifications.send(new Notification(o.userId(), o.email(), event.type().template(), payload));
        } catch (RuntimeException ex) {
            log.error("Failed to queue {} notification for order {}", event.type(), event.orderId(), ex);
        }
    }
}
