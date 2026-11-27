-- =============================================================
-- RBAC (Role-Based Access Control) Tables
-- =============================================================
-- Roles: SUPER_ADMIN, TENANT_ADMIN, CUSTOMER_ADMIN, CUSTOMER_USER
-- SUPER_ADMIN    - Full system access (all customers, all operations)
-- TENANT_ADMIN   - Manage assigned customers (CRUD, config, alerts)
-- CUSTOMER_ADMIN - Manage users and configuration inside one customer
-- CUSTOMER_USER  - Read-only access to assigned customer data
-- =============================================================

CREATE TABLE IF NOT EXISTS auth_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(128),
    email           VARCHAR(255),
    customer_id     VARCHAR(50),          -- NULL for SUPER_ADMIN (all customers)
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_roles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS auth_user_roles (
    user_id         BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role_id         BIGINT NOT NULL REFERENCES auth_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users(username);
CREATE INDEX IF NOT EXISTS idx_auth_users_customer ON auth_users(customer_id);
CREATE INDEX IF NOT EXISTS idx_auth_user_roles_user ON auth_user_roles(user_id);

-- Seed roles
INSERT INTO auth_roles (name, description) VALUES
    ('SUPER_ADMIN',   'Full system access - all customers, all operations'),
    ('TENANT_ADMIN',  'Manage assigned customers - CRUD, config, alerts'),
    ('CUSTOMER_ADMIN','Manage users and configuration inside one customer'),
    ('CUSTOMER_USER', 'Read-only access to assigned customer data')
ON CONFLICT (name) DO NOTHING;

-- No default password or demo user is shipped. The first SUPER_ADMIN is
-- created once through POST /api/v1/auth/bootstrap while auth_users is empty.
