# RBAC and Multi-tenant Architecture Documentation

## 角色等级 (Role Hierarchy)

| Role | Scope | Capabilities |
|------|-------|-------------|
| SUPER_ADMIN | Global | Full system access. Create/manage tenants. Create/manage all users. View all customers across all tenants. System configuration. TIRE plugin management. LLM configuration. |
| TENANT_ADMIN | Tenant-scoped | Acts as distributor/reseller. Manages all customers within their tenant. Views aggregated data across their customers. Creates CUSTOMER_USER accounts for their customers. Cannot see other tenants' data. |
| CUSTOMER_USER | Customer-scoped | Views only their own customer data. Cannot manage other customers or tenants. Limited menu visibility (no Settings, no Customer Management). |

## 数据库架构 (Database Schema)

- `tenants` table: id (serial), name (varchar), description (text), created_at, updated_at
- `customers` table: has `tenant_id` (FK to tenants.id) column
- `auth_users` table: id (serial), username (varchar unique), password_hash (varchar), role (varchar: SUPER_ADMIN/TENANT_ADMIN/CUSTOMER_USER), customer_id (varchar, nullable), tenant_id (bigint, nullable), is_active (boolean), created_at, updated_at
- `auth_roles` table: role name definitions
- `auth_user_roles` table: user-role junction

## 身份验证流程 (Authentication Flow)

1. `POST /api/v1/auth/login` with `{ username, password }`
2. Server validates BCrypt password hash
3. Returns JWT token (1 hour expiry) containing: sub (userId), username, roles, customerId, tenantId
4. All subsequent requests include `Authorization: Bearer <token>`
5. `RbacAuthorizationFilter` (Spring Cloud Gateway GlobalFilter) validates token and injects headers:
   - `X-User-Id`, `X-Username`, `X-User-Roles`, `X-Customer-Id`, `X-Tenant-Id`
6. Downstream services read these headers for scoping

## 权限范围 (Authorization Scoping - RbacAuthorizationFilter)

- **SUPER_ADMIN**: No filtering — sees all data
- **TENANT_ADMIN**: Queries scoped to `tenant_id` — sees all customers under their tenant
  - Assessment API: filtered by tenant's customer list
  - Customer Management: only their tenant's customers
  - Dashboard/Analytics: aggregated multi-customer view
- **CUSTOMER_USER**: Queries scoped to `customer_id` — sees only their own data
  - All data endpoints filtered by customer_id

## 前端基于角色的UI (Frontend Role-Based UI)

- Menu items hidden by role (in `App.tsx`):
  - **CUSTOMER_USER**: No "Customers" page, no "Settings" page
  - **TENANT_ADMIN**: No "Settings" page, has multi-customer Dashboard
  - **SUPER_ADMIN**: Full menu including Settings (TIRE plugins, LLM config, RBAC user management)
- Login page: `/login` route, redirects to `/` on success

## API 接口 (API Endpoints)

- `POST /api/v1/auth/login` — Authenticate
- `POST /api/v1/auth/register` — Create user (SUPER_ADMIN only)
- `GET /api/v1/auth/users` — List users (SUPER_ADMIN only)
- `GET /api/v1/tenants` — List tenants (SUPER_ADMIN only)
- `POST /api/v1/tenants` — Create tenant (SUPER_ADMIN only)
- `GET /api/v1/customers?tenant_id=X` — Scoped customer listing

## 测试凭据 - 开发环境 (Test Credentials - Development)

| Username | Password | Role | Customer | Tenant |
|----------|----------|------|----------|--------|
| admin | admin123 | SUPER_ADMIN | null | null |
| demo_admin | admin123 | TENANT_ADMIN | demo-customer | 1 |
| acme_admin | acme123 | TENANT_ADMIN | acme-customer | 2 |
| acme_user1 | user123 | CUSTOMER_USER | acme-customer | 2 |

## 关键实现文件 (Key Implementation Files)

- `services/api-gateway/src/main/java/com/threatdetection/gateway/auth/` — JWT provider, auth controller, RBAC filter
- `services/api-gateway/src/main/java/com/threatdetection/gateway/auth/filter/RbacAuthorizationFilter.java` — Core authorization
- `frontend/src/App.tsx` — Role-based menu
- `frontend/src/pages/Login/index.tsx` — Login page
- `docker/30-auth-tables.sql` — Auth schema
- `docker/31-rbac-seed-data.sql` — Seed users
