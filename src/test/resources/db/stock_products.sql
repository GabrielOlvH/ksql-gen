-- Stock Products Table
-- Contains information about stock products
CREATE TABLE IF NOT EXISTS stock_products
(
    id            VARCHAR(128) PRIMARY KEY,
    product_code VARCHAR(128),
    name          VARCHAR(255)   NOT NULL,
    description   TEXT           NULL,
    embedding VECTOR(1024) NULL, -- Renamed from description_embedding for consistency
    selling_price NUMERIC(10, 2) NOT NULL,
    cost_price    NUMERIC(10, 2) NOT NULL,
    reorder_point INTEGER        NULL     DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_stock_products_name ON stock_products (name);
CREATE INDEX IF NOT EXISTS idx_stock_products_code ON stock_products (product_code);
CREATE INDEX IF NOT EXISTS idx_stock_products_is_active ON stock_products (is_active);
-- Added HNSW index for vector similarity search on embedding
-- Ensure your pgvector version supports HNSW. For older versions, you might use IVFFlat:
-- CREATE INDEX ON stock_products USING ivfflat (embedding vector_l2_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_hnsw_stock_products_embedding ON stock_products USING hnsw (embedding vector_l2_ops);
