# Threat Detection System — Frontend Restructure Plan

**Date**: 2026-04-25 (updated with RBAC design)
**Prepared by**: Deployment team (based on full source review of 13 pages, 11 service files, 5,732 lines)
**Current rating**: 4/10 — functional React admin app, but not a coherent threat operations product
**Target rating**: 7-8/10 — operator-centric security platform with clear workflows and proper access control

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [RBAC Model & Tenant Hierarchy](#3-rbac-model--tenant-hierarchy)
4. [Proposed Navigation & Page Structure](#4-proposed-navigation--page-structure)
5. [Phase 1 — Critical Fixes + RBAC Foundation (Week 1-3)](#5-phase-1--critical-fixes--rbac-foundation-week-1-3)
6. [Phase 2 — Merge & Restructure (Week 4-5)](#6-phase-2--merge--restructure-week-4-5)
7. [Phase 3 — New Capabilities (Week 6-9)](#7-phase-3--new-capabilities-week-6-9)
8. [Phase 4 — Polish & UX (Week 10-11)](#8-phase-4--polish--ux-week-10-11)
9. [Technical Debt Inventory](#9-technical-debt-inventory)
10. [API Changes Required (Backend)](#10-api-changes-required-backend)
11. [Migration Guide](#11-migration-guide)

---

## 1. Executive Summary

The TDS frontend is a React + Ant Design Pro admin panel with 13 pages and 11 API service files. While individually functional, it has significant structural problems that undermine usability as a **security operations tool**:

| Problem | Impact | Severity |
|---|---|---|
| Dashboard & Analytics are ~70% duplicate | Confuses operators, wastes screen real estate | HIGH |
| Settings is a 1,981-line god page (10+ forms) | Unmaintainable, slow to load, poor UX | HIGH |
| SystemMonitor does browser-side port probing | Cannot work through Nginx reverse proxy; misleading results | CRITICAL |
| Customer/tenant scope is hidden in localStorage | Operators don't know which customer's data they're viewing | CRITICAL |
| ThreatList is too thin (184 lines, no filters/detail/export) | Core security page has less functionality than admin CRUD pages | HIGH |
| No pipeline visibility | Can't distinguish "all clear" from "system is blind" | HIGH |
| Fake multi-region in UI | Region selector in api.ts has no backend support | MEDIUM |
| DeviceMgmt overlaps with CustomerMgmt device sidebar | Two ways to manage devices, inconsistent behavior | MEDIUM |
| Client-side analytics from 200-record sample | Charts look authoritative but represent a tiny sample | MEDIUM |
| Flat navigation with no workflow grouping | 11 top-level menu items with no logical hierarchy | MEDIUM |
| No RBAC — all users see everything | No role-based access; super admin, tenant admin, and customer admin share the same view | CRITICAL |
| No cascading configuration | Settings can't be pushed from super admin → tenant → customer | HIGH |

### Guiding Principles

1. **Operator-first**: A security analyst should land on actionable data, not dashboards
2. **Trust the data**: Never present sampled/incomplete data as authoritative without disclosure
3. **Pipeline transparency**: Operators must always know if the system is actually receiving and processing data
4. **Explicit scope**: The tenant/customer context must be visible, switchable, and role-constrained
5. **Backend-owned health**: Health checks belong on the server, not in the browser
6. **Least privilege**: Each role sees only what it needs; menu items, actions, and data are all scoped by role
7. **Configuration cascading**: Settings flow downward (super admin → tenant → customer) with override capability

---

## 2. Current State Analysis

### 2.1 Page Inventory

| Page | Lines | Role | Problems |
|---|---|---|---|
| **Dashboard** | 373 | 4 stat cards, 24h trend, recent threats, port pie | ~70% overlaps Analytics |
| **Analytics** | 455 | Stat cards, 24h/7d/30d trend, level pie, port bar, top attackers | ~70% overlaps Dashboard; client-side stats from 200 records |
| **ThreatList** | 184 | Paginated table with delete | Too thin for a core page — no filters, no detail view, no export |
| **AlertCenter** | 422 | ProTable with resolve/assign/escalate | Decent but isolated from threats |
| **CustomerMgmt** | 552 | CRUD + device sidebar drawer | Device sidebar overlaps DeviceMgmt |
| **DeviceMgmt** | 260 | Device table + batch bind | Overlaps CustomerMgmt sidebar |
| **ThreatIntel** | 387 | Indicators CRUD, feeds, IP lookup | Standalone, reasonable |
| **MlDetection** | 454 | Health, models, training, buffer/drift/shadow | Good after #21/#22 fixes |
| **SystemMonitor** | 145 | Browser-side fetch to 9 hardcoded `localhost:port` URLs | **Broken by design** — browser can't reach container ports |
| **Settings** | 1,981 | 10+ forms: general, notification, SMTP, customer, network, threat, Logstash, MQTT, LLM, AI/TIRE, API keys | God page — must be split |
| **TenantMgmt** | 174 | CRUD table | Standard admin |
| **UserMgmt** | 241 | CRUD table | Standard admin |
| **Login** | 104 | Login form | Standard |

### 2.2 Service Layer Inventory

| File | Endpoints | Notes |
|---|---|---|
| `api.ts` | Axios instance | Auto-injects `customer_id` from localStorage, snake↔camel converter, region routing (non-functional) |
| `threat.ts` | `/api/v1/assessment/*` | Stats, list, trend, ports; has tenant-variant endpoints |
| `alert.ts` | `/api/v1/alerts/*` | CRUD, resolve, assign, escalate, analytics |
| `customer.ts` | `/api/v1/customers/*` | CRUD, devices, notification-config, net-weights |
| `intel.ts` | `/api/v1/threat-intel/*` | Indicators, feeds, lookup, statistics |
| `ml.ts` | `/api/v1/ml/*` | Health, models, reload, train, status, data-readiness |
| `system.ts` | Hardcoded 9 URLs | **Browser-side health checks — must replace** |
| `config.ts` | `/api/v1/system-config/*` | General config CRUD |
| `tire.ts` | `/api/v1/tire-plugins/*`, `/api/v1/llm-providers/*`, etc. | TIRE integration |
| `tenant.ts` | `/api/v1/tenants/*` | CRUD |
| `user.ts` | `/api/v1/users/*` | CRUD |

### 2.3 Data Flow Issues

```
Current customer_id flow (BROKEN):
  Login → localStorage.setItem("customer_id", ???)
  api.ts interceptor → reads localStorage → injects X-Customer-Id header
  Problem: No UI shows which customer is selected. No way to switch. Silent injection.

Current analytics flow (MISLEADING):
  Analytics page → GET /api/v1/assessment/list?page=1&size=200
  Client-side → counts levels, groups by port, sorts attackers
  Problem: Only 200 records. Presented as "Total Threats: 200" with pie charts.
```

---

## 3. RBAC Model & Tenant Hierarchy

### 3.1 Entity Hierarchy

```
Super Admin (Platform)
  │
  ├─ Tenant A (e.g. "Acme Corp")
  │    ├─ Customer A1 (e.g. "Acme Shanghai Office")
  │    │    ├─ Device A1-001
  │    │    ├─ Device A1-002
  │    │    └─ ...
  │    ├─ Customer A2 (e.g. "Acme Beijing Office")
  │    │    └─ ...
  │    └─ [Tenant-level settings]
  │
  ├─ Tenant B
  │    ├─ Customer B1
  │    │    └─ ...
  │    └─ [Tenant-level settings]
  │
  └─ [Platform-level settings]
```

**Key relationships**:
- A **Super Admin** manages the entire platform: all tenants, all customers, all devices, all settings
- A **Tenant** owns a group of customers. A Tenant Admin manages only their tenant's customers and devices
- A **Customer** owns a group of devices. A Customer Admin manages only their devices and customer-level settings
- **Settings cascade downward**: Super Admin → Tenant → Customer. Each level can override inherited settings

### 3.2 Role Definitions

| Role | Scope | Description |
|---|---|---|
| `super_admin` | Platform-wide | Full access. Manages tenants, pushes global config, views all data across all tenants/customers |
| `tenant_admin` | Single tenant | Manages customers within their tenant, pushes config to customers, views aggregated tenant data |
| `customer_admin` | Single customer | Manages devices, views customer-scoped threats/alerts, configures customer-level settings |
| `operator` | Single customer (read-heavy) | Views threats/alerts/analytics for their customer. Cannot modify settings or manage devices |
| `viewer` | Single customer (read-only) | Dashboard and reports only. No actions, no configuration |

> **Note on `operator` vs `customer_admin`**: An operator is a security analyst who investigates threats. A customer admin is an IT manager who also manages devices and settings. Many deployments will combine these — the separation exists for large customers with dedicated SOC teams.

### 3.3 Permission Matrix

#### Page Access by Role

| Page / Section | super_admin | tenant_admin | customer_admin | operator | viewer |
|---|---|---|---|---|---|
| **Overview** | ✅ (all data) | ✅ (tenant data) | ✅ (customer data) | ✅ | ✅ |
| **Investigate > Alerts** | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Investigate > Threats** | ✅ | ✅ | ✅ | ✅ | ✅ (read) |
| **Investigate > Threat Intel** | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Operate > Pipeline Health** | ✅ | ✅ (own services) | ❌ | ❌ | ❌ |
| **Operate > ML Detection** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Admin > Customers & Devices** | ✅ (all) | ✅ (own tenant) | ✅ (own devices) | ❌ | ❌ |
| **Admin > Users** | ✅ (all) | ✅ (own tenant) | ❌ | ❌ | ❌ |
| **Admin > Tenants** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Config > General** | ✅ | ✅ (tenant-level) | ❌ | ❌ | ❌ |
| **Config > Notifications** | ✅ | ✅ (tenant-level) | ✅ (customer-level) | ❌ | ❌ |
| **Config > Integrations** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Config > AI & LLM** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Config > Plugins** | ✅ | ❌ | ❌ | ❌ | ❌ |

#### Action Permissions

| Action | super_admin | tenant_admin | customer_admin | operator | viewer |
|---|---|---|---|---|---|
| Create/delete tenant | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create/delete customer | ✅ | ✅ (own tenant) | ❌ | ❌ | ❌ |
| Bind/unbind devices | ✅ | ✅ | ✅ (own customer) | ❌ | ❌ |
| Resolve/escalate alerts | ✅ | ✅ | ✅ | ✅ | ❌ |
| Delete threat records | ✅ | ✅ | ❌ | ❌ | ❌ |
| Export data | ✅ | ✅ | ✅ | ✅ | ❌ |
| Push config downstream | ✅ (→ tenant) | ✅ (→ customer) | ❌ | ❌ | ❌ |
| Override inherited config | ✅ | ✅ | ✅ | ❌ | ❌ |
| Train ML models | ✅ | ❌ | ❌ | ❌ | ❌ |
| Manage users | ✅ (all) | ✅ (own tenant) | ❌ | ❌ | ❌ |
| Create/manage API keys | ✅ | ✅ (own scope) | ✅ (own scope) | ❌ | ❌ |

### 3.4 Configuration Cascading Model

Settings flow **top-down** with per-level overrides:

```
┌─ Platform Settings (Super Admin) ──────────────────────────┐
│  notification_email_enabled: true                           │
│  threat_level_thresholds: { critical: 90, high: 70, ... }  │
│  data_retention_days: 90                                    │
│  smtp_host: "mail.platform.com"                             │
│  ...                                                        │
└─────────────────┬──────────────────────────────────────────┘
                  │ push / inherit
                  ▼
┌─ Tenant Settings (Tenant Admin) ───────────────────────────┐
│  notification_email_enabled: true    ← inherited            │
│  threat_level_thresholds: { critical: 85, ... }  ← OVERRIDE│
│  data_retention_days: 90             ← inherited            │
│  smtp_host: "mail.acme.com"          ← OVERRIDE            │
│  ...                                                        │
└─────────────────┬──────────────────────────────────────────┘
                  │ push / inherit
                  ▼
┌─ Customer Settings (Customer Admin) ───────────────────────┐
│  notification_email_enabled: false   ← OVERRIDE            │
│  threat_level_thresholds: { ... }    ← inherited from tenant│
│  data_retention_days: 90             ← inherited from plat  │
│  smtp_host: "mail.acme.com"          ← inherited from tenant│
│  ...                                                        │
└────────────────────────────────────────────────────────────┘
```

**UI behavior for config pages**:

| Scenario | UI Indicator | Actions Available |
|---|---|---|
| Value inherited from parent | Gray text + "Inherited from [Tenant/Platform]" tag | [Override] button |
| Value overridden at this level | Normal text + "Custom" tag | [Edit] [Reset to inherited] buttons |
| Super Admin viewing tenant config | All values shown | [Push to all tenants] [Push to selected] |
| Tenant Admin viewing customer config | All values shown | [Push to all customers] [Push to selected] |

**Push semantics**:
- "Push" = set the child's value to match parent (child can still override later)
- "Force Push" (super_admin only) = set AND lock the value (child cannot override)
- Locked values show a 🔒 icon and disabled form fields

### 3.5 Scope Selector (Header Bar)

The global scope selector in the header adapts to the user's role:

```
┌─────────────────────────────────────────────────────────────────────┐
│  Super Admin view:                                                  │
│  [Tenant: ▼ All Tenants  ] → [Customer: ▼ All Customers  ]  [User]│
│                                                                     │
│  Tenant Admin view:                                                 │
│  [Tenant: Acme Corp (fixed)] → [Customer: ▼ All Customers]  [User]│
│                                                                     │
│  Customer Admin / Operator / Viewer:                                │
│  [Customer: Acme Shanghai Office (fixed)]                    [User]│
└─────────────────────────────────────────────────────────────────────┘
```

**Behavior**:
- **Super Admin**: Can switch between tenants and customers freely. "All Tenants" / "All Customers" shows aggregated data
- **Tenant Admin**: Tenant is fixed (their own). Can switch between customers within that tenant. "All Customers" shows tenant-aggregated data
- **Customer Admin / Operator / Viewer**: Both tenant and customer are fixed. No selector shown — scope badge only

**Implementation**: The scope selector replaces the broken `localStorage.customer_id` injection. The selected scope is stored in React context (`ScopeContext`) and injected into API calls via the Axios interceptor.

### 3.6 Auth Token & Session Design

```
JWT payload:
{
  "sub": "user-uuid",
  "role": "tenant_admin",
  "tenant_id": "tenant-uuid",          // null for super_admin
  "customer_id": "customer-uuid",      // null for super_admin and tenant_admin
  "permissions": ["alerts.resolve", "customers.write", "config.read", ...],
  "iat": 1745000000,
  "exp": 1745086400
}
```

**Frontend auth flow**:
1. Login → receive JWT with role + scope claims
2. Store in `AuthContext` (not localStorage for the token itself)
3. `ScopeContext` initializes from JWT claims:
   - `super_admin` → scope = platform (can narrow down)
   - `tenant_admin` → scope = their tenant (can narrow to customer)
   - `customer_admin` / `operator` / `viewer` → scope = their customer (fixed)
4. Axios interceptor reads from `ScopeContext` and sends `X-Tenant-Id` + `X-Customer-Id` headers
5. Backend validates: JWT scope ⊇ requested scope (tenant_admin can't query other tenants)

### 3.7 Frontend Permission Enforcement

Three layers of enforcement:

**Layer 1 — Route Guard (App.tsx)**:
```tsx
// Route-level access control
<RouteGuard requiredRole="tenant_admin" requiredPermission="config.write">
  <ConfigurationPage />
</RouteGuard>
```
Unauthorized routes redirect to `/overview` with a toast message.

**Layer 2 — Menu Filtering (ProLayout)**:
```tsx
// Filter menu items based on role
const filteredRoutes = allRoutes.filter(route =>
  hasPermission(currentUser.role, route.meta.requiredRole)
);
```
Users never see menu items they can't access.

**Layer 3 — Component-Level Guards**:
```tsx
// Conditionally render action buttons
<PermissionGate permission="alerts.resolve">
  <Button onClick={resolveAlert}>Resolve</Button>
</PermissionGate>

// Or use hook
const canResolve = usePermission("alerts.resolve");
```

**Important**: Frontend checks are for **UX only**. The backend MUST independently enforce all permissions. Never trust the client.

---

## 4. Proposed Navigation & Page Structure

### Current (flat, 11 items):
```
Dashboard | Threats | Analytics | Alerts | Customers | Devices |
Threat Intel | ML Detection | System Monitor | Settings | Tenants | Users
```

### Proposed (grouped, workflow-oriented, role-aware):

```
┌─────────────────────────────────────────────────────────────────────┐
│  [Scope: Tenant ▼] → [Customer ▼]     [Role Badge]  [User] [Logout]│  ← Global header (scope adapts to role)
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  📊 Overview                    (merged Dashboard+Analytics)        │  ALL ROLES
│                                                                     │
│  🔍 Investigate                                                     │  operator+
│     ├─ Alerts                   (existing AlertCenter)              │  operator+
│     ├─ Threats                  (enhanced ThreatList)               │  viewer+ (read), operator+ (actions)
│     └─ Threat Intel             (existing ThreatIntel)              │  operator+
│                                                                     │
│  ⚙️ Operate                                                         │  tenant_admin+
│     ├─ Pipeline Health          (NEW — replaces SysMon)             │  tenant_admin+
│     └─ ML Detection             (existing MlDetection)              │  tenant_admin+ (view), super_admin (train)
│                                                                     │
│  👥 Administration                                                  │  customer_admin+
│     ├─ Customers & Devices      (merged Cust+Device)                │  customer_admin+ (scoped)
│     ├─ Users                    (existing UserMgmt)                 │  tenant_admin+
│     └─ Tenants                  (existing TenantMgmt)               │  super_admin only
│                                                                     │
│  🔧 Configuration                                                   │  customer_admin+ (partial)
│     ├─ General                  (from Settings)                     │  tenant_admin+
│     ├─ Notifications & SMTP     (from Settings)                     │  customer_admin+ (own level)
│     ├─ Integrations (MQTT/Log)  (from Settings)                     │  super_admin only
│     ├─ AI & LLM                 (from Settings)                     │  super_admin only
│     └─ Plugins (TIRE)           (from Settings)                     │  super_admin only
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Role → Default Landing Page

| Role | Landing Page | Rationale |
|---|---|---|
| `super_admin` | Overview (all tenants) | Platform-wide health at a glance |
| `tenant_admin` | Overview (own tenant) | Tenant-aggregated threats |
| `customer_admin` | Overview (own customer) | Customer-specific threats |
| `operator` | Alerts | Actionable items first |
| `viewer` | Overview | Read-only dashboard |

**Key changes:**
- 11 flat items → 5 groups with clear purpose
- Scope selector is **global, explicit, and role-constrained** in the header
- Menu items are **filtered by role** — users never see pages they can't access
- Dashboard + Analytics merged (eliminate duplication)
- CustomerMgmt + DeviceMgmt merged (eliminate overlap)
- Settings split into 5 focused sub-pages with role-scoped access
- SystemMonitor replaced by backend-powered Pipeline Health
- ThreatList enhanced to be worthy of its "core page" status
- Configuration pages show inherited/overridden values with cascading push

---

## 5. Phase 1 — Critical Fixes + RBAC Foundation (Week 1-3)

These are **urgent** because they cause incorrect behavior or misleading data. RBAC foundation is added here because the scope selector and auth redesign must happen before restructuring pages.

### 5.1 Replace SystemMonitor with Backend Health Endpoint

**Problem**: `SystemMonitor` (145 lines) does `fetch("http://localhost:8080/actuator/health")` etc. from the browser. This:
- Cannot work through Nginx (browser can't reach container ports)
- Exposes internal architecture to the client
- Shows false negatives (everything appears "down")

**Solution**:

1. **Backend**: Add `GET /api/v1/system/health` endpoint to `api-gateway` that aggregates health from all internal services:

```json
// GET /api/v1/system/health
{
  "status": "healthy",          // healthy | degraded | unhealthy
  "timestamp": "2026-04-25T...",
  "services": {
    "data-ingestion":      { "status": "UP", "latency_ms": 12 },
    "stream-processing":   { "status": "UP", "latency_ms": 8 },
    "threat-assessment":   { "status": "UP", "latency_ms": 15 },
    "alert-management":    { "status": "UP", "latency_ms": 10 },
    "customer-management": { "status": "UP", "latency_ms": 11 },
    "threat-intelligence": { "status": "UP", "latency_ms": 9 },
    "ml-detection":        { "status": "UP", "latency_ms": 14 },
    "kafka":               { "status": "UP" },
    "redis":               { "status": "UP" },
    "postgres":            { "status": "UP" },
    "emqx":                { "status": "UP" },
    "logstash":            { "status": "UP" }
  },
  "pipeline": {
    "last_event_received": "2026-04-25T10:30:00Z",
    "events_last_hour": 1247,
    "kafka_lag": 0,
    "flink_running": true
  }
}
```

2. **Frontend**: Replace `system.ts` hardcoded URLs with single API call. Replace `SystemMonitor` page with `PipelineHealth` page that renders the above.

**Files to change**:
- `services/system.ts` → rewrite to call `/api/v1/system/health`
- `pages/SystemMonitor/index.tsx` → replace with `pages/PipelineHealth/index.tsx`
- Backend: new endpoint in `api-gateway` (Spring Boot)

**Effort**: ~2 days (1 backend, 1 frontend)

---

### 5.2 Make Tenant/Customer Scope Explicit + Role-Aware

**Problem**: `api.ts` silently reads `customer_id` from localStorage and injects it as a header. There is no UI showing which customer is currently selected, and no way to switch without manually editing localStorage. There is no concept of roles — all users see everything.

**Solution** (implements Section 3.5 and 3.7):

1. **Create `AuthContext` and `ScopeContext`**:

```tsx
// src/contexts/AuthContext.tsx
interface AuthState {
  user: { id: string; name: string; role: Role; tenantId?: string; customerId?: string };
  permissions: string[];
  token: string;
}

// src/contexts/ScopeContext.tsx
interface ScopeState {
  tenantId: string | null;    // null = "all tenants" (super_admin only)
  tenantName: string | null;
  customerId: string | null;  // null = "all customers" (super_admin/tenant_admin)
  customerName: string | null;
  setTenant: (id: string, name: string) => void;   // super_admin only
  setCustomer: (id: string, name: string) => void;  // super_admin/tenant_admin
}
```

2. **Add role-aware scope selector to the ProLayout header** (see Section 3.5 for wireframe):
   - Super Admin: tenant dropdown + customer dropdown
   - Tenant Admin: fixed tenant badge + customer dropdown
   - Customer Admin / Operator / Viewer: fixed scope badge only

3. **Update `api.ts`** to read from `ScopeContext` and send both `X-Tenant-Id` + `X-Customer-Id` headers.

4. **Add route guards and menu filtering** (see Section 3.7):
   - `RouteGuard` component wraps protected routes
   - `PermissionGate` component for inline action buttons
   - `usePermission(perm)` hook for conditional logic
   - ProLayout menu filtered by `currentUser.role`

5. **Show scope badge** on all data pages indicating current scope.

**Files to change**:
- NEW: `src/contexts/AuthContext.tsx`
- NEW: `src/contexts/ScopeContext.tsx`
- NEW: `src/components/RouteGuard.tsx`
- NEW: `src/components/PermissionGate.tsx`
- NEW: `src/components/ScopeSelector.tsx`
- NEW: `src/hooks/usePermission.ts`
- `src/services/api.ts` → read from ScopeContext, send X-Tenant-Id + X-Customer-Id
- `src/App.tsx` → wrap with providers, add scope selector to header, filter menu by role
- `src/pages/Login/index.tsx` → parse JWT claims, initialize AuthContext
- All pages that use customer-scoped data → wrap actions with PermissionGate

**Backend requirements**:
- Login endpoint must return JWT with `role`, `tenant_id`, `customer_id` claims (see Section 3.6)
- All API endpoints must validate scope: `JWT scope ⊇ requested scope`
- New endpoint: `GET /api/v1/auth/me` → returns user profile with role and scope

**Effort**: ~4 days (2 frontend contexts + guards, 1 backend JWT changes, 1 integration + testing)

---

### 5.3 Disable Region Switching

**Problem**: `api.ts` contains a region-to-URL map and `setRegion()`/`getRegion()` functions, but there is only one backend deployment. The region selector (if exposed) would silently break all API calls.

**Solution**: Remove `regionConfig`, `setRegion`, `getRegion` from `api.ts`. Hardcode `baseURL` to `/api`. If multi-region is planned for the future, add it back when the backend supports it.

**Files to change**:
- `src/services/api.ts`

**Effort**: ~1 hour

---

### 5.4 Fix Client-Side Analytics Accuracy

**Problem**: Analytics page fetches 200 records via `GET /api/v1/assessment/list?size=200` and computes all charts client-side. The "Total Threats" counter shows `200` (the page size), and pie/bar charts only reflect that sample.

**Solution** (two options):

**Option A (Quick fix)**: Add a disclaimer banner:
```
⚠️ Analytics based on most recent 200 events. For full statistics, use the export feature.
```

**Option B (Proper fix — recommended)**: Create backend aggregation endpoints:
```
GET /api/v1/assessment/stats          → { total, by_level: {...}, by_port: {...} }
GET /api/v1/assessment/trend?period=  → [{ date, count }]
GET /api/v1/assessment/top-attackers  → [{ ip, count }]
```
Then have Analytics call these instead of doing client-side aggregation.

**Recommendation**: Option A for Phase 1, Option B in Phase 2.

**Effort**: Option A: 1 hour. Option B: 2-3 days (backend + frontend).

---

## 6. Phase 2 — Merge & Restructure (Week 4-5)

### 6.1 Merge Dashboard + Analytics → Overview

**Current duplication**:

| Feature | Dashboard | Analytics | Keep in Overview |
|---|---|---|---|
| Stat cards (total/high/medium/low) | ✅ | ✅ | ✅ (one set) |
| 24h threat trend line | ✅ | ✅ (+ 7d/30d toggle) | ✅ with period toggle |
| Recent threats table | ✅ | ❌ | ✅ |
| Port distribution pie | ✅ | ✅ (bar) | ✅ (pick one) |
| Threat level pie | ❌ | ✅ | ✅ |
| Top attackers | ❌ | ✅ | ✅ |

**New Overview page** (~400-500 lines):
```
┌─────────────┬─────────────┬─────────────┬─────────────┐
│ Total: 1247 │ Critical: 3 │ High: 45    │ Medium: 199 │  ← Stat cards
└─────────────┴─────────────┴─────────────┴─────────────┘
┌──────────────────────────────┬──────────────────────────┐
│  Threat Trend (24h/7d/30d)   │  Level Distribution      │  ← Charts row
│  [line chart]                │  [pie chart]             │
├──────────────────────────────┼──────────────────────────┤
│  Top Attacked Ports          │  Top Attackers           │
│  [bar chart]                 │  [table: IP, count, geo] │
├──────────────────────────────┴──────────────────────────┤
│  Recent Threats [table, 10 rows, link to full list]     │  ← Quick glance
└─────────────────────────────────────────────────────────┘
```

**Files to create/modify**:
- DELETE: `pages/Dashboard/index.tsx`, `pages/Analytics/index.tsx`
- CREATE: `pages/Overview/index.tsx`
- UPDATE: `App.tsx` routing

**Effort**: ~3 days

---

### 6.2 Merge CustomerMgmt + DeviceMgmt → Customers & Devices

**Current overlap**: CustomerMgmt has a device sidebar drawer. DeviceMgmt has a standalone device table with batch bind. Both manage devices but with different UX flows.

**New unified page**:
```
┌─ Customers & Devices ──────────────────────────────────┐
│                                                         │
│  [Tab: Customers]  [Tab: Devices]                       │
│                                                         │
│  Customers tab:                                         │
│    ProTable of customers                                │
│    Click row → expand inline device list                │
│    Actions: Add Customer, Edit, Delete                  │
│                                                         │
│  Devices tab:                                           │
│    ProTable of ALL devices (with customer column)       │
│    Filter by customer, status, type                     │
│    Actions: Batch Bind, Edit, Sync                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Files to create/modify**:
- DELETE: `pages/DeviceMgmt/index.tsx`
- REWRITE: `pages/CustomerMgmt/index.tsx` → `pages/CustomersAndDevices/index.tsx` with tabs
- UPDATE: `App.tsx` routing

**Effort**: ~2 days

---

### 6.3 Split Settings (1,981 lines) → 5 Sub-Pages with Role-Scoped Access & Config Cascading

**Current Settings contains these sections** (all in one file):

| Section | Approx Lines | Target Sub-Page | Min Role |
|---|---|---|---|
| General config (system name, language, retention) | ~200 | `Configuration/General` | tenant_admin |
| Notification rules + templates | ~300 | `Configuration/Notifications` | customer_admin |
| SMTP settings | ~150 | `Configuration/Notifications` | tenant_admin |
| Customer config (thresholds, weights) | ~250 | Move to CustomerMgmt | customer_admin |
| Network config (subnets, VLANs) | ~200 | `Configuration/General` | tenant_admin |
| Threat rules (levels, scoring) | ~200 | `Configuration/General` | tenant_admin |
| Logstash config | ~150 | `Configuration/Integrations` | super_admin |
| MQTT config | ~150 | `Configuration/Integrations` | super_admin |
| LLM providers + models | ~200 | `Configuration/AI` | super_admin |
| AI/TIRE plugin config | ~180 | `Configuration/Plugins` | super_admin |

**New structure**:
```
/configuration/general         → tenant_admin+   (general + network + threat rules)
/configuration/notifications   → customer_admin+ (notification rules; SMTP = tenant_admin+)
/configuration/integrations    → super_admin     (Logstash + MQTT)
/configuration/ai              → super_admin     (LLM providers + models)
/configuration/plugins         → super_admin     (TIRE integration)
```

**Config cascading UI** (implements Section 3.4):

Each config page must show the **effective value** with its **source**:

```
┌─ Notifications Config ─────────────────────────────────────────────┐
│                                                                     │
│  Scope: [Viewing as: Tenant "Acme Corp"]                           │
│                                                                     │
│  Email Notifications                                                │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ Enabled:  ✅ Yes      [Inherited from Platform]  [Override] │    │
│  │ From:     admin@acme.com  [Custom ✏️]        [Reset to inherited]│
│  │ Template: "..."       [Inherited from Platform]  [Override] │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  [Push to All Customers]  [Push to Selected Customers...]           │
│                                                                     │
│  ── Customer Overrides ────────────────────────────────────────     │
│  │ Customer          │ Email Enabled │ From Address    │ Status │   │
│  │ Acme Shanghai     │ ❌ No (custom) │ (inherited)     │ 1 override│
│  │ Acme Beijing      │ (inherited)   │ bj@acme.com     │ 1 override│
│  │ Acme Guangzhou    │ (inherited)   │ (inherited)     │ clean     │
└─────────────────────────────────────────────────────────────────────┘
```

**Implementation approach**:
1. Create `pages/Configuration/` directory with `index.tsx` (tab/menu layout)
2. Extract each section into a sub-component: `GeneralConfig.tsx`, `NotificationConfig.tsx`, etc.
3. Each sub-component gets its own state management (no more single-page state blob)
4. Customer-specific thresholds/weights move to the Customers page as a config tab
5. **NEW**: Each config value carries metadata: `{ value, source: "platform"|"tenant"|"customer", locked: boolean }`
6. **NEW**: "Push" and "Reset to inherited" actions per field
7. **NEW**: Override summary table showing which children have customized values
8. **NEW**: Route guards on each sub-page based on role (see permission matrix in Section 3.3)

**Backend requirements**:
- Config API must return `{ value, source, locked }` for each field
- New endpoints:
  - `POST /api/v1/config/push` — push config from parent scope to children
  - `POST /api/v1/config/lock` — lock a field (super_admin only)
  - `DELETE /api/v1/config/{key}/override` — reset to inherited value
  - `GET /api/v1/config/overrides?scope=tenant&id=xxx` — list child overrides

**Files to create/modify**:
- DELETE: `pages/Settings/index.tsx`
- CREATE: `pages/Configuration/index.tsx` (layout with side menu + route guards)
- CREATE: `pages/Configuration/GeneralConfig.tsx`
- CREATE: `pages/Configuration/NotificationConfig.tsx`
- CREATE: `pages/Configuration/IntegrationConfig.tsx`
- CREATE: `pages/Configuration/AIConfig.tsx`
- CREATE: `pages/Configuration/PluginConfig.tsx`
- CREATE: `components/ConfigField.tsx` (reusable: shows value + source tag + override/reset actions)
- CREATE: `components/ConfigPushDialog.tsx` (push to children dialog)
- UPDATE: `App.tsx` routing with route guards

**Effort**: ~6-7 days (more than original estimate due to cascading UI + role guards)

---

## 7. Phase 3 — New Capabilities (Week 6-9)

### 7.1 Enhance ThreatList → Proper Threat Investigation Page

**Current state** (184 lines): Paginated table with columns (ID, type, level, source IP, target, timestamp) and a delete action. No filtering, no detail view, no export.

**Target state** (~600-800 lines):

```
┌─ Threats ──────────────────────────────────────────────┐
│                                                         │
│  Filters: [Level ▼] [Type ▼] [Date Range] [Source IP]  │
│           [Device ▼] [Customer ▼] [Search...]           │
│                                                         │
│  [Export CSV] [Export JSON]          Showing 1-50 of 847│
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ID │ Level │ Type │ Source │ Target │ Device │ Time│  │
│  │ ── │ ───── │ ──── │ ────── │ ────── │ ────── │ ───│  │
│  │ Click any row to expand detail panel ──────────► │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  Detail Panel (slide-in drawer):                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Threat ID: TH-2026-0425-001                      │   │
│  │ Raw Event Data (JSON)                             │   │
│  │ Related Alerts (linked)                           │   │
│  │ Device Info (linked to device page)               │   │
│  │ Timeline (other events from same source)          │   │
│  │ Actions: [Create Alert] [Add to Intel] [Dismiss]  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

**Key additions**:
- Server-side filtering (requires backend filter params on `/api/v1/assessment/list`)
- Detail drawer with raw event data
- Cross-links to alerts and devices
- Export functionality
- Bulk actions (dismiss, escalate)

**Effort**: ~5 days (2 backend for filters/export endpoints, 3 frontend)

---

### 7.2 Pipeline Health Page (replaces SystemMonitor)

Built on the backend endpoint from Phase 1 (4.1), this page shows:

```
┌─ Pipeline Health ──────────────────────────────────────┐
│                                                         │
│  Overall: 🟢 Healthy          Last event: 2s ago        │
│                                                         │
│  ┌─ Ingestion ──────┐  ┌─ Processing ──┐  ┌─ Storage ─┐│
│  │ Logstash: 🟢 UP  │  │ Flink: 🟢 UP  │  │ PG: 🟢 UP ││
│  │ EMQX: 🟢 UP      │→│ Kafka lag: 0  │→│ Redis: 🟢  ││
│  │ Events/hr: 1247  │  │ Jobs: 3/3     │  │ Disk: 45% ││
│  └──────────────────┘  └───────────────┘  └───────────┘│
│                                                         │
│  ┌─ Services ──────────────────────────────────────────┐│
│  │ Service              │ Status │ Latency │ Last Check ││
│  │ data-ingestion       │ 🟢 UP  │ 12ms    │ 10s ago    ││
│  │ stream-processing    │ 🟢 UP  │ 8ms     │ 10s ago    ││
│  │ threat-assessment    │ 🟢 UP  │ 15ms    │ 10s ago    ││
│  │ ...                  │        │         │            ││
│  └─────────────────────────────────────────────────────┘│
│                                                         │
│  ⚠️ Alerts:                                             │
│  │ ML models not loaded — training required             │
│  │ Kafka consumer lag > 100 on topic X (if applicable)  │
└─────────────────────────────────────────────────────────┘
```

**Key feature**: Auto-refresh every 10s. If `last_event_received` is >5 min old, show warning: "No events received in 5 minutes — check Logstash connectivity."

**Effort**: ~3 days (1 backend refinement, 2 frontend)

---

### 7.3 Backend Aggregation Endpoints for Analytics

Replace client-side aggregation (Option B from Phase 1):

```
GET /api/v1/assessment/stats
  → { total: 1247, critical: 3, high: 45, medium: 199, low: 1000 }

GET /api/v1/assessment/trend?period=24h|7d|30d
  → [{ timestamp: "...", count: 12 }, ...]

GET /api/v1/assessment/top-ports?limit=10
  → [{ port: 22, count: 340 }, { port: 443, count: 280 }, ...]

GET /api/v1/assessment/top-attackers?limit=10
  → [{ ip: "1.2.3.4", count: 89, country: "CN" }, ...]
```

**Effort**: ~2 days (backend SQL aggregation queries + REST endpoints)

---

## 8. Phase 4 — Polish & UX (Week 10-11)

### 8.1 Implement New Navigation Layout

Update `App.tsx` ProLayout route config to match the proposed structure from Section 3. Group items under collapsible menu sections.

### 8.2 Add Breadcrumbs and Page Titles

Every page should show: `Configuration > Notifications` not just "Settings".

### 8.3 Responsive Design Audit

Current pages use hardcoded `Col span` values. Add responsive breakpoints for tablet/laptop screens.

### 8.4 Loading States and Error Boundaries

Several pages have no loading indicators or error handling for failed API calls. Add:
- Skeleton loaders for tables/charts
- Error boundary component with retry
- Empty states ("No threats detected" vs spinner)

### 8.5 Keyboard Navigation and Accessibility

- Tab order for forms
- ARIA labels on charts
- Keyboard shortcuts for common actions (R = refresh, / = search)

**Effort**: ~5 days total

---

## 9. Technical Debt Inventory

### Must Fix

| Item | File(s) | Effort |
|---|---|---|
| Remove `regionConfig` / `setRegion` / `getRegion` | `services/api.ts` | 1h |
| Replace localStorage `customer_id` with ScopeContext | `services/api.ts`, all pages | Part of Phase 1 RBAC |
| Remove browser-side health checks | `services/system.ts` | 1h |
| Extract shared chart components (used in Dashboard AND Analytics) | `pages/Dashboard/`, `pages/Analytics/` | 1d |
| Type safety: several `any` casts in service files | `services/*.ts` | 1d |
| No role/permission checks anywhere in frontend | All routes, all action buttons | Part of Phase 1 RBAC |
| Login page doesn't parse JWT claims for role/scope | `pages/Login/index.tsx` | Part of Phase 1 RBAC |

### Should Fix

| Item | File(s) | Effort |
|---|---|---|
| Consistent error handling pattern across pages | All `pages/*/index.tsx` | 2d |
| Extract common table config (ProTable shared columns) | Multiple pages | 1d |
| Standardize date formatting (some use `dayjs`, some inline) | Multiple pages | 0.5d |
| Remove unused imports (several pages have dead imports) | Multiple pages | 0.5d |
| Audit all API calls for proper tenant/customer scope headers | `services/*.ts` | 1d |

### Nice to Have

| Item | File(s) | Effort |
|---|---|---|
| Dark mode support | Global theme | 3d |
| i18n (currently hardcoded Chinese with some English) | All pages | 5d |
| Unit tests (currently 0% coverage) | All pages | 5-10d |
| Role-based E2E tests (test each role sees correct menu/data) | Playwright tests | 3d |

---

## 10. API Changes Required (Backend)

The frontend restructure requires these **new or modified backend endpoints**:

### RBAC & Auth Endpoints (Phase 1)

| Endpoint | Service | Purpose |
|---|---|---|
| `POST /api/v1/auth/login` (enhanced) | api-gateway | Return JWT with `role`, `tenant_id`, `customer_id` claims |
| `GET /api/v1/auth/me` | api-gateway | Current user profile with role, scope, permissions |
| `GET /api/v1/roles` | api-gateway | List available roles (super_admin use) |
| `PUT /api/v1/users/{id}/role` | api-gateway | Assign role to user |
| **All existing endpoints** | all services | Must validate `X-Tenant-Id` + `X-Customer-Id` headers against JWT scope |

### Config Cascading Endpoints (Phase 2)

| Endpoint | Service | Purpose |
|---|---|---|
| `GET /api/v1/config?scope={platform\|tenant\|customer}&id=xxx` | config-server | Get effective config with source metadata |
| `PUT /api/v1/config/{key}` | config-server | Set config value at current scope |
| `DELETE /api/v1/config/{key}/override` | config-server | Reset to inherited value |
| `POST /api/v1/config/push` | config-server | Push config from parent to children |
| `POST /api/v1/config/lock` | config-server | Lock field (super_admin only) |
| `GET /api/v1/config/overrides?scope=tenant&id=xxx` | config-server | List child override summary |

### Data & Pipeline Endpoints (Phase 3)

| Endpoint | Service | Purpose |
|---|---|---|
| `GET /api/v1/system/health` | api-gateway | Aggregated health from all services |
| `GET /api/v1/assessment/stats` | threat-assessment | Server-side threat statistics |
| `GET /api/v1/assessment/trend` | threat-assessment | Server-side trend aggregation |
| `GET /api/v1/assessment/top-ports` | threat-assessment | Server-side port distribution |
| `GET /api/v1/assessment/top-attackers` | threat-assessment | Server-side attacker ranking |
| `GET /api/v1/assessment/list` (enhanced) | threat-assessment | Add filter params: `level`, `type`, `source_ip`, `device_id`, `date_from`, `date_to` |
| `GET /api/v1/assessment/export` | threat-assessment | CSV/JSON export with same filters |
| `GET /api/v1/assessment/{id}` | threat-assessment | Single threat detail with raw event |

### Database Schema Changes

```sql
-- Users table: add role and scope columns
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'viewer';
ALTER TABLE users ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE users ADD COLUMN customer_id UUID REFERENCES customers(id);

-- Config inheritance table
CREATE TABLE config_values (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  scope_type VARCHAR(10) NOT NULL CHECK (scope_type IN ('platform', 'tenant', 'customer')),
  scope_id UUID,                    -- null for platform scope
  config_key VARCHAR(255) NOT NULL,
  config_value JSONB NOT NULL,
  locked BOOLEAN DEFAULT false,     -- super_admin can lock
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(scope_type, scope_id, config_key)
);

-- Effective config view (resolves inheritance)
CREATE OR REPLACE FUNCTION get_effective_config(
  p_scope_type VARCHAR, p_scope_id UUID
) RETURNS TABLE(config_key VARCHAR, config_value JSONB, source_type VARCHAR, source_id UUID, locked BOOLEAN)
AS $$ ... $$;   -- walks up: customer → tenant → platform, returns first match per key
```

**No breaking changes** — all new endpoints are additive. Existing endpoints remain unchanged. The JWT enhancement is backward-compatible (old tokens without role claim default to `super_admin` during migration).

---

## 11. Migration Guide

### For the Development Team

#### Step-by-step execution order:

```
Week 1:  Phase 1.1 (SystemMonitor → backend health)          — CRITICAL
         Phase 1.3 (remove region switching)                  — 1 hour
Week 2:  Phase 1.2 (RBAC foundation: AuthContext, ScopeContext, route guards, JWT)  — CRITICAL
         Phase 1.4 (analytics disclaimer)                     — 1 hour
Week 3:  Phase 1.2 continued (menu filtering, PermissionGate, backend scope validation)
Week 4:  Phase 2.1 (merge Dashboard + Analytics → Overview)
Week 5:  Phase 2.2 (merge CustomerMgmt + DeviceMgmt)
         Phase 2.3 (split Settings + config cascading — start)
Week 6:  Phase 2.3 (split Settings + config cascading — finish, push/lock UI)
Week 7:  Phase 3.1 (enhance ThreatList — start)
Week 8:  Phase 3.2 (Pipeline Health page)
         Phase 3.3 (backend aggregation endpoints)
Week 9:  Phase 3.1 (ThreatList — finish + test)
Week 10: Phase 4 (navigation, breadcrumbs, responsive)
Week 11: Phase 4 (loading states, role-based QA testing, polish)
```

#### File deletion checklist (after migration):

```
DELETE pages/Dashboard/index.tsx          (replaced by Overview)
DELETE pages/Analytics/index.tsx          (replaced by Overview)
DELETE pages/SystemMonitor/index.tsx      (replaced by PipelineHealth)
DELETE pages/DeviceMgmt/index.tsx         (merged into CustomersAndDevices)
DELETE pages/Settings/index.tsx           (split into Configuration/*)
```

#### New files:

```
CREATE contexts/AuthContext.tsx
CREATE contexts/ScopeContext.tsx
CREATE components/RouteGuard.tsx
CREATE components/PermissionGate.tsx
CREATE components/ScopeSelector.tsx
CREATE components/ConfigField.tsx              (inherited/override value display)
CREATE components/ConfigPushDialog.tsx          (push config to children)
CREATE hooks/usePermission.ts
CREATE pages/Overview/index.tsx
CREATE pages/PipelineHealth/index.tsx
CREATE pages/CustomersAndDevices/index.tsx
CREATE pages/Configuration/index.tsx
CREATE pages/Configuration/GeneralConfig.tsx
CREATE pages/Configuration/NotificationConfig.tsx
CREATE pages/Configuration/IntegrationConfig.tsx
CREATE pages/Configuration/AIConfig.tsx
CREATE pages/Configuration/PluginConfig.tsx
```

#### Route changes in App.tsx:

```tsx
// REMOVE
{ path: '/dashboard',      component: Dashboard }
{ path: '/analytics',      component: Analytics }
{ path: '/system-monitor',  component: SystemMonitor }
{ path: '/device-mgmt',    component: DeviceMgmt }
{ path: '/settings',       component: Settings }

// ADD (with role guards — see Section 3.3 permission matrix)
{ path: '/',                         redirect: '/overview' }
{ path: '/overview',                 component: Overview,              roles: ['*'] }
{ path: '/investigate/alerts',       component: AlertCenter,           roles: ['super_admin','tenant_admin','customer_admin','operator'] }
{ path: '/investigate/threats',      component: ThreatList,            roles: ['*'] }  // viewer = read-only
{ path: '/investigate/intel',        component: ThreatIntel,           roles: ['super_admin','tenant_admin','customer_admin','operator'] }
{ path: '/operate/pipeline',         component: PipelineHealth,        roles: ['super_admin','tenant_admin'] }
{ path: '/operate/ml',               component: MlDetection,           roles: ['super_admin','tenant_admin'] }
{ path: '/admin/customers',          component: CustomersAndDevices,   roles: ['super_admin','tenant_admin','customer_admin'] }
{ path: '/admin/users',              component: UserMgmt,              roles: ['super_admin','tenant_admin'] }
{ path: '/admin/tenants',            component: TenantMgmt,            roles: ['super_admin'] }
{ path: '/config/general',           component: GeneralConfig,         roles: ['super_admin','tenant_admin'] }
{ path: '/config/notifications',     component: NotificationConfig,    roles: ['super_admin','tenant_admin','customer_admin'] }
{ path: '/config/integrations',      component: IntegrationConfig,     roles: ['super_admin'] }
{ path: '/config/ai',                component: AIConfig,              roles: ['super_admin'] }
{ path: '/config/plugins',           component: PluginConfig,          roles: ['super_admin'] }
```

---

## Summary

| Phase | Duration | Key Deliverables | Effort (person-days) |
|---|---|---|---|
| Phase 1 — Critical Fixes + RBAC Foundation | Week 1-3 | Backend health endpoint, RBAC contexts/guards/JWT, scope selector, menu filtering | ~8 days |
| Phase 2 — Merge & Restructure | Week 4-6 | Overview (merge 2), Customers & Devices (merge 2), Settings → 5 sub-pages with config cascading | ~12 days |
| Phase 3 — New Capabilities | Week 7-9 | Enhanced ThreatList, Pipeline Health, backend aggregation endpoints | ~12 days |
| Phase 4 — Polish | Week 10-11 | Navigation, breadcrumbs, responsive, loading states, role-based QA | ~6 days |
| **Total** | **~11 weeks** | | **~38 person-days** |

After completion, the frontend will be:
- **Operator-centric**: workflow-grouped navigation, actionable threat detail, pipeline visibility
- **Trustworthy**: server-side analytics, explicit scope, real health checks
- **Secure**: RBAC with 5 role tiers, route guards, component-level permission gates, backend scope validation
- **Multi-tenant native**: explicit tenant/customer hierarchy, cascading configuration with push/lock/override
- **Maintainable**: no god pages, no duplicate pages, clear component boundaries
- **Estimated rating**: 7-8/10
