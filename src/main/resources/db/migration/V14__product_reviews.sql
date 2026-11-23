CREATE TABLE product_review (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, user_id)
);

CREATE INDEX product_review_product_idx ON product_review (product_id, id DESC);
CREATE INDEX product_review_user_idx ON product_review (user_id);

-- Denormalized aggregates keep product lists free of per-row review queries.
ALTER TABLE product
    ADD COLUMN review_count BIGINT NOT NULL DEFAULT 0 CHECK (review_count >= 0),
    ADD COLUMN rating_total BIGINT NOT NULL DEFAULT 0 CHECK (rating_total >= 0);
