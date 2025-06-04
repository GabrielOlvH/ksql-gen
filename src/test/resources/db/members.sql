-- Members Table
-- Contains information about members of organizations
CREATE TABLE IF NOT EXISTS members
(
    user_id         VARCHAR(255) NOT NULL,
    organization_id VARCHAR(255) NOT NULL,
    permissions     TEXT         NOT NULL,
    PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_members_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE ON UPDATE CASCADE
);
