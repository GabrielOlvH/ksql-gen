-- Organizations Table
-- Contains information about organizations
CREATE TABLE IF NOT EXISTS organizations
(
    id                 VARCHAR(255) PRIMARY KEY,
    icon               TEXT                     NOT NULL,
    owner_id           VARCHAR(255)             NOT NULL,
    name               VARCHAR(255)             NOT NULL,
    tax_id             VARCHAR(255)             NOT NULL,
    alias              VARCHAR(255)             NOT NULL,
    main_activity      VARCHAR(255)             NULL,
    side_activity      VARCHAR(255)             NULL,
    contact_email      VARCHAR(255)             NULL,
    contact_phone      VARCHAR(50)              NULL,
    address_street     VARCHAR(255)             NOT NULL,
    address_city       VARCHAR(255)             NOT NULL,
    address_district   VARCHAR(255)             NOT NULL,
    address_number     VARCHAR(255)             NOT NULL,
    address_state_code VARCHAR(255)             NOT NULL,
    address_country    VARCHAR(255)             NOT NULL,
    address_zip_code   VARCHAR(255)             NOT NULL,
    address_details    VARCHAR(255)             NULL,
    created_at         TIMESTAMP WITH TIME ZONE NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NULL
);
CREATE INDEX IF NOT EXISTS idx_organizations_name ON organizations (name);
CREATE INDEX IF NOT EXISTS idx_organizations_tax_id ON organizations (tax_id);
