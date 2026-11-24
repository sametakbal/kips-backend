package com.akbal.kips.be.service.notification;

public interface NotificationService {
    /**
     * Queues a notification for delivery. Implementations persist in their own transaction so
     * they can be called after the caller's transaction has committed.
     */
    void send(Notification notification);
}
