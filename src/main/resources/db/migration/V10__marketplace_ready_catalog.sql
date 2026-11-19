CREATE TABLE product_family (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

ALTER TABLE product
    ADD COLUMN sku VARCHAR(64),
    ADD COLUMN gtin VARCHAR(14),
    ADD COLUMN family_id BIGINT REFERENCES product_family (id),
    ADD COLUMN catalog_details JSONB;

-- Keep legacy rows that predate the NOT VALID constraints eligible for the SKU backfill.
ALTER TABLE product
    DROP CONSTRAINT product_price_nonnegative,
    DROP CONSTRAINT product_stock_nonnegative,
    DROP CONSTRAINT product_category_required;

UPDATE product SET sku = 'KIPS-' || id;

ALTER TABLE product
    ALTER COLUMN sku SET NOT NULL,
    ADD CONSTRAINT product_sku_unique UNIQUE (sku),
    ADD CONSTRAINT product_gtin_format CHECK (gtin ~ '^([0-9]{8}|[0-9]{12,14})$'),
    ADD CONSTRAINT product_price_nonnegative CHECK (price >= 0) NOT VALID,
    ADD CONSTRAINT product_stock_nonnegative CHECK (stock >= 0) NOT VALID,
    ADD CONSTRAINT product_category_required CHECK (category_id IS NOT NULL) NOT VALID;

CREATE UNIQUE INDEX product_gtin_unique ON product (lpad(gtin, 14, '0')) WHERE gtin IS NOT NULL;
CREATE INDEX product_family_idx ON product (family_id);
CREATE UNIQUE INDEX product_family_variant_unique ON product (family_id, (catalog_details -> 'variantOptions'))
    WHERE family_id IS NOT NULL
      AND jsonb_typeof(catalog_details -> 'variantOptions') = 'object'
      AND catalog_details -> 'variantOptions' <> '{}'::jsonb;

CREATE TABLE marketplace_listing (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('AMAZON', 'HEPSIBURADA', 'TRENDYOL', 'N11')),
    account_key VARCHAR(64) NOT NULL,
    market_key VARCHAR(64) NOT NULL,
    channel_sku VARCHAR(255) NOT NULL,
    channel_barcode VARCHAR(64),
    external_catalog_id VARCHAR(128),
    external_listing_id VARCHAR(128),
    category_reference VARCHAR(128),
    brand_reference VARCHAR(128),
    product_type VARCHAR(128),
    group_reference VARCHAR(128),
    sale_price NUMERIC(19, 2),
    list_price NUMERIC(19, 2),
    currency VARCHAR(3),
    channel_attributes JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE (product_id, channel, account_key, market_key),
    UNIQUE (channel, account_key, market_key, channel_sku),
    CHECK ((sale_price IS NULL) = (currency IS NULL)),
    CHECK (sale_price >= 0),
    CHECK (list_price IS NULL OR (sale_price IS NOT NULL AND list_price >= sale_price)),
    CHECK (jsonb_typeof(channel_attributes) = 'object')
);
