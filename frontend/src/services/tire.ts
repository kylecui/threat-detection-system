import apiClient from '@/services/api';
export interface TireCustomPlugin {
  id: number;
  name: string;
  slug: string;
  description: string;
  pluginUrl: string;
  hasApiKey: boolean;
  apiKey: string;
  authType: string;
  authHeader: string;
  parserType: string;
  requestMethod: string;
  requestBody: string | null;
  responsePath: string | null;
  enabled: boolean;
  priority: number;
  timeout: number;
  ownerType: 'SYSTEM' | 'TENANT' | 'USER';
  ownerId: number | null;
  createdAt: string;
  updatedAt: string;
}

export async function listTirePlugins(): Promise<TireCustomPlugin[]> {
  const resp = await apiClient.get<TireCustomPlugin[]>('/api/v1/tire-plugins');
  return resp.data;
}

export async function createTirePlugin(data: Partial<TireCustomPlugin>): Promise<TireCustomPlugin> {
  const resp = await apiClient.post<TireCustomPlugin>('/api/v1/tire-plugins', data);
  return resp.data;
}

export async function updateTirePlugin(id: number, data: Partial<TireCustomPlugin>): Promise<TireCustomPlugin> {
  const resp = await apiClient.put<TireCustomPlugin>(`/api/v1/tire-plugins/${id}`, data);
  return resp.data;
}

export async function deleteTirePlugin(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/tire-plugins/${id}`);
}

export interface LlmProvider {
  id: number;
  name: string;
  model: string;
  baseUrl: string;
  hasApiKey: boolean;
  apiKey: string;
  isDefault: boolean;
  enabled: boolean;
  ownerType: 'SYSTEM' | 'TENANT' | 'USER';
  ownerId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface LlmValidateResult {
  ok: boolean;
  models: string[];
  error: string | null;
}

export async function listLlmProviders(): Promise<LlmProvider[]> {
  const resp = await apiClient.get<LlmProvider[]>('/api/v1/llm-providers');
  return resp.data;
}

export async function createLlmProvider(data: Partial<LlmProvider>): Promise<LlmProvider> {
  const resp = await apiClient.post<LlmProvider>('/api/v1/llm-providers', data);
  return resp.data;
}

export async function updateLlmProvider(id: number, data: Partial<LlmProvider>): Promise<LlmProvider> {
  const resp = await apiClient.put<LlmProvider>(`/api/v1/llm-providers/${id}`, data);
  return resp.data;
}

export async function deleteLlmProvider(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/llm-providers/${id}`);
}

export async function validateLlmProvider(id: number): Promise<LlmValidateResult> {
  const resp = await apiClient.post<LlmValidateResult>(`/api/v1/llm-providers/${id}/validate`);
  return resp.data;
}

export interface ConfigAssignment {
  id?: number;
  customerId: string;
  llmProviderId: number;
  assignedBy: number;
  providerName?: string;
  providerModel?: string;
  providerBaseUrl?: string;
  providerEnabled?: boolean;
  assigned?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export async function getConfigAssignment(customerId: string): Promise<ConfigAssignment> {
  const resp = await apiClient.get<ConfigAssignment>(`/api/v1/config-assignments/${customerId}`);
  return resp.data;
}

export async function assignConfig(customerId: string, llmProviderId: number): Promise<ConfigAssignment> {
  const resp = await apiClient.put<ConfigAssignment>(`/api/v1/config-assignments/${customerId}`, { llmProviderId });
  return resp.data;
}

export async function unassignConfig(customerId: string): Promise<void> {
  await apiClient.delete(`/api/v1/config-assignments/${customerId}`);
}

export async function listConfigAssignments(): Promise<ConfigAssignment[]> {
  const resp = await apiClient.get<ConfigAssignment[]>('/api/v1/config-assignments');
  return resp.data;
}
