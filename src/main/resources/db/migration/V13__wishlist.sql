CREATE TABLE wishlist_item (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX wishlist_item_product_idx ON wishlist_item (product_id);
