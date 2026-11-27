import apiClient from '@/services/api';
import type { SystemConfig, SystemConfigCategory } from '@/types';

const CATEGORY_ORDER: Record<SystemConfigCategory, string[]> = {
  llm: ['LLM_API_KEY', 'LLM_MODEL', 'LLM_BASE_URL'],
  tire_api_keys: [
    'ABUSEIPDB_API_KEY',
    'OTX_API_KEY',
    'GREYNOISE_API_KEY',
    'VT_API_KEY',
    'SHODAN_API_KEY',
    'THREATBOOK_API_KEY',
    'TIANJIYOUMENG_API_KEY',
  ],
  tire_general: ['TIRE_ADMIN_PASSWORD', 'TIRE_CACHE_TTL_HOURS'],
  tire_plugins: [],
};

function sortConfigs(category: SystemConfigCategory, configs: SystemConfig[]): SystemConfig[] {
  const order = CATEGORY_ORDER[category];
  return [...configs].sort((left, right) => {
    const leftIndex = order.indexOf(left.key);
    const rightIndex = order.indexOf(right.key);
    if (leftIndex !== -1 || rightIndex !== -1) {
      if (leftIndex === -1) return 1;
      if (rightIndex === -1) return -1;
      return leftIndex - rightIndex;
    }
    return left.id - right.id || left.key.localeCompare(right.key);
  });
}

export async function getConfigsByCategory(category: SystemConfigCategory): Promise<SystemConfig[]> {
  const resp = await apiClient.get<SystemConfig[]>(`/api/v1/system-config/category/${category}`);
  return sortConfigs(category, resp.data);
}

export async function batchUpdateConfigs(configs: Record<string, string>): Promise<void> {
  await apiClient.put('/api/v1/system-config/batch', { configs });
}

export async function updateConfig(key: string, value: string): Promise<void> {
  await apiClient.put(`/api/v1/system-config/${key}`, { value });
}

export interface LlmValidateResult {
  ok: boolean;
  models: string[];
  error: string | null;
}

export async function validateLlmConnection(
  apiKey: string,
  baseUrl: string,
  model: string,
): Promise<LlmValidateResult> {
  const resp = await apiClient.post<LlmValidateResult>('/api/v1/system-config/llm/validate', {
    api_key: apiKey,
    base_url: baseUrl,
    model,
  });
  return resp.data;
}
