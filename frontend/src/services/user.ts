import apiClient from '@/services/api';
import type { ManagedUser, CreateUserRequest, UpdateUserRequest } from '@/types';

export async function listUsers(tenantId?: number): Promise<ManagedUser[]> {
  const params = tenantId ? { tenantId } : {};
  const resp = await apiClient.get<ManagedUser[]>('/api/v1/users', { params });
  return resp.data;
}

export async function getUser(id: number): Promise<ManagedUser> {
  const resp = await apiClient.get<ManagedUser>(`/api/v1/users/${id}`);
  return resp.data;
}

export async function createUser(req: CreateUserRequest): Promise<ManagedUser> {
  const resp = await apiClient.post<ManagedUser>('/api/v1/users', req);
  return resp.data;
}

export async function updateUser(id: number, req: UpdateUserRequest): Promise<ManagedUser> {
  const resp = await apiClient.put<ManagedUser>(`/api/v1/users/${id}`, req);
  return resp.data;
}

export async function deleteUser(id: number): Promise<void> {
  await apiClient.delete(`/api/v1/users/${id}`);
}
