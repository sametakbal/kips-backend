CREATE TABLE staff_role_permission (
    role_id BIGINT NOT NULL REFERENCES app_user_roles (id) ON DELETE CASCADE,
    permission VARCHAR(32) NOT NULL CHECK (permission IN (
        'CATALOG_EDIT', 'INVENTORY_ADJUST', 'ORDER_READ_FULFILLMENT', 'ORDER_SHIP', 'ORDER_READ_SUPPORT'
    )),
    PRIMARY KEY (role_id, permission)
);

INSERT INTO staff_role_permission (role_id, permission)
SELECT r.id, defaults.permission
FROM (VALUES
    ('ROLE_CATALOG_STAFF', 'CATALOG_EDIT'),
    ('ROLE_FULFILLMENT_STAFF', 'INVENTORY_ADJUST'),
    ('ROLE_FULFILLMENT_STAFF', 'ORDER_READ_FULFILLMENT'),
    ('ROLE_FULFILLMENT_STAFF', 'ORDER_SHIP'),
    ('ROLE_SUPPORT_AGENT', 'ORDER_READ_SUPPORT')
) AS defaults(role_name, permission)
JOIN app_user_roles r ON r.name = defaults.role_name;
