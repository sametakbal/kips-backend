package com.akbal.kips.be.service.notification;

import java.util.Map;
import java.util.Objects;

/**
 * A message addressed to a single recipient. {@code template} names the message kind; delivery
 * channels render it with {@code payload}.
 */
public record Notification(Long userId, String recipient, String template, Map<String, Object> payload) {
    public Notification {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(template, "template");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
