INSERT INTO app_user_roles (created_at, name)
VALUES (CURRENT_TIMESTAMP, 'ROLE_CATALOG_STAFF'),
       (CURRENT_TIMESTAMP, 'ROLE_FULFILLMENT_STAFF'),
       (CURRENT_TIMESTAMP, 'ROLE_SUPPORT_AGENT')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE staff_audit_event (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id BIGINT NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    previous_value JSONB NOT NULL,
    new_value JSONB NOT NULL,
    reason VARCHAR(500)
);

CREATE INDEX staff_audit_event_target_idx ON staff_audit_event (target_type, target_id, id);
