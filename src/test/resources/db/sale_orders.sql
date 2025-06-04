-- Sale Orders Table
-- Contains information about sale orders
CREATE TABLE IF NOT EXISTS sale_orders
(
    so_number             VARCHAR(128) PRIMARY KEY,
    status                VARCHAR(50)              NOT NULL DEFAULT 'pending',
    user_id               VARCHAR(128)             NULL,
    date_created          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_confirmed        TIMESTAMP WITH TIME ZONE NULL,
    date_fulfilled        TIMESTAMP WITH TIME ZONE NULL,
    order_date            DATE                     NULL,
    customer_id           VARCHAR(128)             NULL,
    customer_name         VARCHAR(255)             NULL,
    customer_email        VARCHAR(255)             NULL,
    customer_phone_number VARCHAR(50)              NULL,
    shipping_address      TEXT                     NULL,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    notes        TEXT           NULL, -- Added notes field
    embedding VECTOR(1024) NULL       -- Renamed from notes_embedding for consistency
);
CREATE INDEX IF NOT EXISTS idx_sale_orders_status ON sale_orders (status);
CREATE INDEX IF NOT EXISTS idx_sale_orders_date_created ON sale_orders (date_created);
CREATE INDEX IF NOT EXISTS idx_sale_orders_customer_id ON sale_orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_orders_customer_name ON sale_orders (customer_name);
CREATE INDEX IF NOT EXISTS idx_sale_orders_order_date ON sale_orders (order_date);
CREATE INDEX IF NOT EXISTS idx_sale_orders_user_id ON sale_orders (user_id);
-- Added HNSW index for vector similarity search on embedding
CREATE INDEX IF NOT EXISTS idx_hnsw_sale_orders_embedding ON sale_orders USING hnsw (embedding vector_l2_ops);

-- Sale Order Items Table
-- Contains items associated with sale orders
CREATE TABLE IF NOT EXISTS sale_order_items
(
    so_id              VARCHAR(128)   NOT NULL,
    product_id         VARCHAR(128)   NOT NULL,
    quantity_ordered   INTEGER        NOT NULL,
    quantity_fulfilled INTEGER        NULL DEFAULT 0,
    quantity_shipped   INTEGER        NULL DEFAULT 0,
    unit_price         NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_sale_order_items_so FOREIGN KEY (so_id) REFERENCES sale_orders (so_number) ON DELETE CASCADE,
    CONSTRAINT fk_sale_order_items_product FOREIGN KEY (product_id) REFERENCES stock_products (id) ON DELETE RESTRICT,
    CONSTRAINT pk_sale_orders PRIMARY KEY (so_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_sale_order_items_so_id ON sale_order_items (so_id);
CREATE INDEX IF NOT EXISTS idx_sale_order_items_product_id ON sale_order_items (product_id);
