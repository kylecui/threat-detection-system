# TIRE & LLM Integration Documentation / TIRE 与 LLM 集成文档

## 1. TIRE 概述 / TIRE Overview

- **TIRE (Threat Intelligence Reputation Engine)**: A specialized engine for evaluating the reputation of various indicators of compromise (IOCs).
- **Origin**: Originally developed as a standalone project [ipit](https://github.com/kylecui/ipit).
- **Integration**: Integrated into the threat detection system as a microservice.
- **Deployment**: Runs as the `tire` service in the production Docker Compose stack; Kubernetes manifests are optional deployment references, not the single-node release path.
- **Network**: Internal port `8000`, accessed through the API gateway.

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

平台级配置保存在 `system_config` 的 `llm` 分类，多供应商和多租户配置保存在 `llm_providers`、`user_configs` 和 `config_assignments`。主要字段为 `LLM_API_KEY`、`LLM_MODEL`、`LLM_BASE_URL`；API Key 在数据库中使用 AES-256-GCM 密文保存。

### 动态运行时读取

1. 用户从 Web 端请求生成 TIRE 报告。
2. API Gateway 在认证后向 TIRE 传递用户、客户和租户上下文。
3. TIRE 使用独立的 `TIRE_INTERNAL_SERVICE_TOKEN` 请求 `GET /internal/v1/runtime-config/llm/effective`。
4. Gateway 按管理员锁定配置、用户自有配置、管理员分配配置、系统默认配置的规则返回有效模型、Base URL、临时使用的明文 API Key 与配置指纹。
5. TIRE 仅在内存中短时缓存配置；配置指纹进入报告缓存键。Gateway 暂时不可用时，可在限定的 stale TTL 内使用最后成功值。运行时桥接已启用但没有有效缓存时执行 fail-closed：禁止本地/环境 LLM 密钥回退，仅生成模板报告；只有未配置运行时桥接的旧版独立部署才使用 TIRE 本地解析器。

内部接口不接受浏览器 JWT 作为服务身份，不向公开页面暴露，响应设置 `Cache-Control: no-store`。Compose 首次安装自动生成内部令牌和 32 字节配置加密密钥，升级时保留。

### 连接验证 / Connection Validation

- **Endpoint**: `POST /api/v1/system-config/llm/validate`
- **Role**: `SUPER_ADMIN`
- **Request Body**:
  ```json
  {
    "api_key": "sk-...",
    "base_url": "https://api.openai.com/v1",
    "model": "gpt-4o-mini"
  }
  ```
- **Mechanism**: 使用所选模型调用最小 `/chat/completions` 生成请求；模型列表查询为可选补充，不能替代真实生成验证。
- **Compatibility**: 支持实现 OpenAI-compatible Chat Completions API 的服务。供应商若只实现其他协议，需要增加适配器。

## 6. 前端界面 / Frontend UI

平台配置工作区的 AI 配置页提供全局 API Key、模型、Base URL、连接测试、多供应商和配置分配。保存后无需重启 TIRE；新生成或明确重新生成的报告读取最新有效配置，已有缓存报告不会自动重算。

## 7. 威胁情报增强流程 / Threat Intelligence Enrichment Flow

The system enriches attack events with threat intelligence to refine threat scores.

```text
Attack event → Flink scoring → Threat Intelligence Service (8085) → TIRE (8000)
                                        ↓
                            IOC lookup + plugin queries
                                        ↓
                            intelWeight factor (1.0-4.5) applied to threat score
```

## 8. 关键实现文件 / Key Implementation Files

- **Database**: `docker/33-plugin-configs.sql` (Seed data).
- **Backend (API Gateway)**:
  - `SystemConfigController.java`: Plugin CRUD and LLM validation logic.
  - `InternalLlmRuntimeConfigController.java`: token-protected runtime config endpoint.
  - `LlmRuntimeConfigService.java`: effective config resolution.
  - `SecretEncryptionService.java`: AES-256-GCM secret storage.
  - `SystemConfig.java`: Entity definition.
  - `SystemConfigRepository.java`: R2DBC repository for database access.
- **Frontend**:
  - `frontend/src/pages/Configuration/AIConfig.tsx`: AI configuration and validation UI.
  - `frontend/src/services/config.ts`: `validateLlmConnection()` API integration.
  - `frontend/src/types/index.ts`: Type definitions including `tire_plugins` category.
- **TIRE**:
  - `services/tire/app/runtime_llm_config.py`: request-context lookup, TTL cache and stale fallback.
  - `services/tire/app/api.py`: resolves effective configuration before report generation.
