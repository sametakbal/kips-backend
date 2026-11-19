CREATE TABLE inventory_adjustment_receipt (
    product_id BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    actor_user_id BIGINT NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    delta INTEGER NOT NULL CHECK (delta <> 0),
    reason VARCHAR(500) NOT NULL,
    resulting_stock INTEGER NOT NULL CHECK (resulting_stock >= 0),
    PRIMARY KEY (product_id, actor_user_id, request_key)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM marketplace_listing
        WHERE channel = 'HEPSIBURADA'
        GROUP BY account_key, market_key, upper(channel_sku)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Resolve case-insensitive Hepsiburada SKU conflicts before migrating';
    END IF;
END $$;

UPDATE marketplace_listing
SET channel_sku = upper(channel_sku)
WHERE channel = 'HEPSIBURADA' AND channel_sku <> upper(channel_sku);

CREATE UNIQUE INDEX marketplace_hepsiburada_sku_unique
    ON marketplace_listing (account_key, market_key, upper(channel_sku))
    WHERE channel = 'HEPSIBURADA';

ALTER TABLE marketplace_listing
    ALTER COLUMN sale_price TYPE NUMERIC(21, 4),
    ALTER COLUMN list_price TYPE NUMERIC(21, 4);
