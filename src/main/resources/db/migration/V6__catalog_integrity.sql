SELECT setval(
    pg_get_serial_sequence('product_category', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM product_category),
             (SELECT last_value FROM product_category_id_seq))
);

ALTER TABLE product
    ADD CONSTRAINT product_price_nonnegative CHECK (price >= 0) NOT VALID,
    ADD CONSTRAINT product_stock_nonnegative CHECK (stock >= 0) NOT VALID,
    ADD CONSTRAINT product_category_required CHECK (category_id IS NOT NULL) NOT VALID;
