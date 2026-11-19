CREATE TABLE cart_item (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX cart_item_product_idx ON cart_item (product_id);

CREATE TABLE shop_order (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PAID', 'SHIPPED', 'CANCELLED')),
    payment_method VARCHAR(16) NOT NULL CHECK (payment_method = 'LOCAL_TEST'),
    currency CHAR(3) NOT NULL,
    total NUMERIC NOT NULL CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX shop_order_user_id_idx ON shop_order (user_id, id DESC);

CREATE TABLE shop_order_item (
    order_id BIGINT NOT NULL REFERENCES shop_order (id),
    product_id BIGINT NOT NULL REFERENCES product (id),
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE INDEX shop_order_item_product_idx ON shop_order_item (product_id);
