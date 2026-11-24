package com.akbal.kips.be.service.notification;

/** Published inside the order transaction; listeners react only after it commits. */
public record OrderEvent(Type type, long orderId) {
    public enum Type {
        CREATED("ORDER_CREATED"),
        SHIPPED("ORDER_SHIPPED"),
        CANCELLED("ORDER_CANCELLED");

        private final String template;

        Type(String template) {
            this.template = template;
        }

        public String template() {
            return template;
        }
    }
}
