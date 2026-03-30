import apiClient from '@/services/api';
import type { Tenant, CreateTenantRequest, UpdateTenantRequest } from '@/types';

export async function listTenants(): Promise<Tenant[]> {
  const resp = await apiClient.get<Tenant[]>('/api/v1/tenants');
  return resp.data;
}

export async function getTenant(id: number): Promise<Tenant> {
  const resp = await apiClient.get<Tenant>(`/api/v1/tenants/${id}`);
  return resp.data;
}

export async function createTenant(req: CreateTenantRequest): Promise<Tenant> {
  const resp = await apiClient.post<Tenant>('/api/v1/tenants', req);
  return resp.data;
}

export async function updateTenant(id: number, req: UpdateTenantRequest): Promise<Tenant> {
  const resp = await apiClient.put<Tenant>(`/api/v1/tenants/${id}`, req);
  return resp.data;
}

export async function deleteTenant(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/tenants/${id}`);
}
