-- Add the customer-scoped administrator role for both fresh installs and upgrades.
INSERT INTO auth_roles (name, description)
VALUES ('CUSTOMER_ADMIN', 'Manage users and configuration inside one customer')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO tds_schema_migrations (migration_name)
VALUES ('43-customer-admin-role.sql')
ON CONFLICT (migration_name) DO NOTHING;
