-- Suppliers Table
-- Contains information about suppliers
CREATE TABLE IF NOT EXISTS suppliers
(
    id             VARCHAR(128) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    contact_name   VARCHAR(255) NULL,
    address        TEXT         NULL,
    email          VARCHAR(255) NULL,
    phone          VARCHAR(50)  NULL,
    lead_time_days INTEGER      NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    embedding VECTOR(1024) NULL, -- Renamed from info_embedding for consistency
    CONSTRAINT uq_suppliers_email UNIQUE (email)
);
CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers (name);
CREATE INDEX IF NOT EXISTS idx_suppliers_is_active ON suppliers (is_active);
-- Added HNSW index for vector similarity search on embedding
CREATE INDEX IF NOT EXISTS idx_hnsw_suppliers_embedding ON suppliers USING hnsw (embedding vector_l2_ops);
