CREATE TABLE product
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       DECIMAL(19, 2) NOT NULL,
    stock       INTEGER NOT NULL,
    category_id BIGINT,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP,

    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id)
            REFERENCES product_category (id)
);