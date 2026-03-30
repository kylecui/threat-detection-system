# TIRE & LLM Integration Documentation / TIRE 与 LLM 集成文档

## 1. TIRE 概述 / TIRE Overview

- **TIRE (Threat Intelligence Reputation Engine)**: A specialized engine for evaluating the reputation of various indicators of compromise (IOCs).
- **Origin**: Originally developed as a standalone project [ipit](https://github.com/kylecui/ipit).
- **Integration**: Integrated into the threat detection system as a microservice.
- **Deployment**: Runs as a separate pod in the K3s namespace `threat-detection`.
- **Network**: Internal port `5000`, accessible via the API gateway.

## 2. 威胁情报插件 / Threat Intelligence Plugins

The system supports 11 threat intelligence plugins, each with configurable status, priority, and timeout.

| Plugin | Description | Config Keys |
|--------|-------------|-------------|
| abuseipdb | AbuseIPDB IP reputation database | PLUGIN_ABUSEIPDB_ENABLED, PLUGIN_ABUSEIPDB_PRIORITY, PLUGIN_ABUSEIPDB_TIMEOUT |
| virustotal | VirusTotal malware/URL analysis | PLUGIN_VIRUSTOTAL_ENABLED, PLUGIN_VIRUSTOTAL_PRIORITY, PLUGIN_VIRUSTOTAL_TIMEOUT |
| otx | AlienVault Open Threat Exchange | PLUGIN_OTX_ENABLED, PLUGIN_OTX_PRIORITY, PLUGIN_OTX_TIMEOUT |
| greynoise | GreyNoise internet scanner detection | PLUGIN_GREYNOISE_ENABLED, PLUGIN_GREYNOISE_PRIORITY, PLUGIN_GREYNOISE_TIMEOUT |
| shodan | Shodan internet-connected device search | PLUGIN_SHODAN_ENABLED, PLUGIN_SHODAN_PRIORITY, PLUGIN_SHODAN_TIMEOUT |
| rdap | RDAP domain/IP registration data | PLUGIN_RDAP_ENABLED, PLUGIN_RDAP_PRIORITY, PLUGIN_RDAP_TIMEOUT |
| reverse_dns | Reverse DNS lookup | PLUGIN_REVERSE_DNS_ENABLED, PLUGIN_REVERSE_DNS_PRIORITY, PLUGIN_REVERSE_DNS_TIMEOUT |
| honeynet | Honeynet Project threat data | PLUGIN_HONEYNET_ENABLED, PLUGIN_HONEYNET_PRIORITY, PLUGIN_HONEYNET_TIMEOUT |
| internal_flow | Internal network flow analysis | PLUGIN_INTERNAL_FLOW_ENABLED, PLUGIN_INTERNAL_FLOW_PRIORITY, PLUGIN_INTERNAL_FLOW_TIMEOUT |
| threatbook | 微步在线 (Threatbook) Chinese threat intel | PLUGIN_THREATBOOK_ENABLED, PLUGIN_THREATBOOK_PRIORITY, PLUGIN_THREATBOOK_TIMEOUT |
| tianjiyoumeng | 天际友盟 (TianjiYoumeng) Chinese threat intel | PLUGIN_TIANJIYOUMENG_ENABLED, PLUGIN_TIANJIYOUMENG_PRIORITY, PLUGIN_TIANJIYOUMENG_TIMEOUT |

### 配置说明 / Configuration Details
- **ENABLED**: Boolean (true/false) to toggle the plugin.
- **PRIORITY**: Integer (1-100) to determine the weight of the plugin's findings.
- **TIMEOUT**: Integer (seconds) for the plugin's API request timeout.
- **Authentication**: Plugins like `threatbook` and `tianjiyoumeng` require additional API keys: `THREATBOOK_API_KEY`, `TIANJIYOUMENG_API_KEY`.

## 3. 插件配置存储 / Plugin Configuration Storage

- **Table**: `system_config`
- **Category**: `tire_plugins`
- **Rows**: 35 total rows.
  - 33 rows for 11 plugins (Enabled, Priority, Timeout).
  - 2 rows for API keys (Threatbook, TianjiYoumeng).
- **Seed Data**: Initialized via `docker/33-plugin-configs.sql`.

## 4. 插件管理 API / Plugin Management API

All endpoints require the `SUPER_ADMIN` role.

- **GET `/api/v1/system-config/category/tire_plugins`**: Retrieves all 33 plugin configuration rows.
- **PUT `/api/v1/system-config/{id}`**: Updates a specific configuration entry by ID.
- **PUT `/api/v1/system-config/batch`**: Performs a batch update of multiple configuration entries.

## 5. LLM 集成 / LLM Integration

LLM configurations are stored in the `system_config` table under the `llm` category.

### 配置项 / Configuration Keys
- `llm_api_key`: API key for the LLM provider.
- `llm_base_url`: Base URL for the API (e.g., `https://api.openai.com/v1`).
- `llm_model`: The specific model to use (e.g., `gpt-4`).
- `llm_enabled`: Boolean to enable/disable LLM features.

### 连接验证 / Connection Validation
- **Endpoint**: `POST /api/v1/system-config/llm/validate`
- **Role**: `SUPER_ADMIN`
- **Request Body**:
  ```json
  {
    "api_key": "sk-...",
    "base_url": "https://api.openai.com/v1"
  }
  ```
- **Mechanism**: The server uses `WebClient` to call the provider's `/models` endpoint.
- **Response**:
  ```json
  {
    "ok": true,
    "models": ["gpt-4", "gpt-3.5-turbo", ...],
    "error": null
  }
  ```
- **Compatibility**: Supports any OpenAI-compatible API (OpenAI, Azure OpenAI, local LLM servers like Ollama/vLLM).

## 6. 前端界面 / Frontend UI

The Settings page (`/settings`) is restricted to the `SUPER_ADMIN` role and contains the following tabs:

1. **系统配置 (System Config)**: General system-wide settings.
2. **插件管理 (Plugin Management)**: A grid layout for the 11 TIRE plugins, featuring toggle switches for status, and numeric inputs for priority and timeout.
3. **LLM配置 (LLM Config)**:
   - Inputs for API Key and Base URL.
   - **测试连接 (Test Connection)** button to trigger validation.
   - Model selection dropdown, dynamically populated from the validation response.
4. **RBAC管理 (RBAC Management)**: User and role management.

## 7. 威胁情报增强流程 / Threat Intelligence Enrichment Flow

The system enriches attack events with threat intelligence to refine threat scores.

```text
Attack event → Flink scoring → Threat Intelligence Service (8085) → TIRE (5000)
                                        ↓
                            IOC lookup + plugin queries
                                        ↓
                            intelWeight factor (1.0-4.5) applied to threat score
```

## 8. 关键实现文件 / Key Implementation Files

- **Database**: `docker/33-plugin-configs.sql` (Seed data).
- **Backend (API Gateway)**:
  - `SystemConfigController.java`: Plugin CRUD and LLM validation logic.
  - `SystemConfig.java`: Entity definition.
  - `SystemConfigRepository.java`: R2DBC repository for database access.
- **Frontend**:
  - `frontend/src/pages/Settings/index.tsx`: UI implementation for Plugin and LLM tabs (lines 817-998).
  - `frontend/src/services/config.ts`: `validateLlmConnection()` API integration.
  - `frontend/src/types/index.ts`: Type definitions including `tire_plugins` category.
