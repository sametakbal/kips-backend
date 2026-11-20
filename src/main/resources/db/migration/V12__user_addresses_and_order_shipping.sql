CREATE TABLE user_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label VARCHAR(32) NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX user_address_user_idx ON user_address (user_id);
CREATE UNIQUE INDEX user_address_default_unique ON user_address (user_id) WHERE is_default;

ALTER TABLE shop_order
    ADD COLUMN address_id BIGINT REFERENCES user_address (id) ON DELETE SET NULL,
    ADD COLUMN shipping_recipient_name VARCHAR(150),
    ADD COLUMN shipping_phone VARCHAR(20),
    ADD COLUMN shipping_line1 VARCHAR(255),
    ADD COLUMN shipping_line2 VARCHAR(255),
    ADD COLUMN shipping_city VARCHAR(120),
    ADD COLUMN shipping_state VARCHAR(120),
    ADD COLUMN shipping_postal_code VARCHAR(20),
    ADD COLUMN shipping_country CHAR(2);

-- Backfill placeholder shipping data for orders placed before address capture existed.
UPDATE shop_order
SET shipping_recipient_name = 'Unknown',
    shipping_phone = 'N/A',
    shipping_line1 = 'N/A',
    shipping_city = 'N/A',
    shipping_postal_code = 'N/A',
    shipping_country = 'TR'
WHERE shipping_recipient_name IS NULL;

ALTER TABLE shop_order
    ALTER COLUMN shipping_recipient_name SET NOT NULL,
    ALTER COLUMN shipping_phone SET NOT NULL,
    ALTER COLUMN shipping_line1 SET NOT NULL,
    ALTER COLUMN shipping_city SET NOT NULL,
    ALTER COLUMN shipping_postal_code SET NOT NULL,
    ALTER COLUMN shipping_country SET NOT NULL;

CREATE INDEX shop_order_address_idx ON shop_order (address_id);
