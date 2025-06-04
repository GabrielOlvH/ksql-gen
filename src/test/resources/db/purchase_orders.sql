-- Purchase Orders Table
-- Contains information about purchase orders
CREATE TABLE IF NOT EXISTS purchase_orders
(
    po_number              VARCHAR(128) PRIMARY KEY,
    status                 VARCHAR(50)              NOT NULL DEFAULT 'ordered',
    user_id                VARCHAR(128)             NULL,
    date_created           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_ordered           DATE                     NULL,
    date_received          TIMESTAMP WITH TIME ZONE NULL,
    expected_delivery_date DATE                     NULL,
    supplier_id            VARCHAR(128)             NOT NULL,
    total_amount           NUMERIC(12, 2)           NOT NULL DEFAULT 0.00,
    notes     TEXT        NULL, -- Added notes field
    embedding VECTOR(1024) NULL, -- Renamed from notes_embedding for consistency
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders (status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_date_created ON purchase_orders (date_created);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_id ON purchase_orders (supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_date_ordered ON purchase_orders (date_ordered);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_user_id ON purchase_orders (user_id);
-- Added HNSW index for vector similarity search on embedding
CREATE INDEX IF NOT EXISTS idx_hnsw_purchase_orders_embedding ON purchase_orders USING hnsw (embedding vector_l2_ops);

CREATE TABLE IF NOT EXISTS purchase_order_items
(
    po_id             VARCHAR(128)   NOT NULL,
    product_id        VARCHAR(128)   NOT NULL,
    quantity_ordered  INTEGER        NOT NULL,
    quantity_received INTEGER        NULL DEFAULT 0,
    unit_price        NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_purchase_order_items_po FOREIGN KEY (po_id) REFERENCES purchase_orders (po_number) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items_product FOREIGN KEY (product_id) REFERENCES stock_products (id) ON DELETE RESTRICT,
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (po_id, product_id)
);
