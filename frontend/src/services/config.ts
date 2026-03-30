import apiClient from '@/services/api';
import type { SystemConfig, SystemConfigCategory } from '@/types';

export async function getConfigsByCategory(category: SystemConfigCategory): Promise<SystemConfig[]> {
  const resp = await apiClient.get<SystemConfig[]>(`/api/v1/system-config/category/${category}`);
  return resp.data;
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

export async function validateLlmConnection(apiKey: string, baseUrl: string): Promise<LlmValidateResult> {
  const resp = await apiClient.post<LlmValidateResult>('/api/v1/system-config/llm/validate', {
    api_key: apiKey,
    base_url: baseUrl,
  });
  return resp.data;
}
