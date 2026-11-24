package com.akbal.kips.be.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxNotificationService implements NotificationService {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Notification notification) {
        long id = jdbc.sql("""
                INSERT INTO notification_outbox (user_id, channel, recipient, template, payload)
                VALUES (:user, 'EMAIL', :recipient, :template, CAST(:payload AS jsonb))
                RETURNING id
                """)
                .param("user", notification.userId())
                .param("recipient", notification.recipient())
                .param("template", notification.template())
                .param("payload", json.writeValueAsString(notification.payload()))
                .query(Long.class).single();
        log.info("Queued {} notification #{} for user {}", notification.template(), id, notification.userId());
    }
}
