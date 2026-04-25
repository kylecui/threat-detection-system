import apiClient from '@/services/api';

export interface ConfigInheritanceItem {
  id: number;
  entityType: 'tenant' | 'customer';
  entityId: number;
  configType: string;
  lockMode: 'default' | 'inherit_only' | 'independent_only';
  lockedBy: number | null;
  lockedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EntityGroup {
  id: number;
  groupName: string;
  groupType: 'tenant_group' | 'customer_group';
  description: string | null;
  tenantId: number | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface EntityGroupMember {
  id: number;
  groupId: number;
  entityId: number;
  entityType: 'tenant' | 'customer';
  addedAt: string;
}

export async function getEntityLocks(entityType: 'tenant' | 'customer', entityId: number): Promise<ConfigInheritanceItem[]> {
  const resp = await apiClient.get<ConfigInheritanceItem[]>(`/api/v1/config-inheritance/${entityType}/${entityId}`);
  return resp.data;
}

export async function getEntityLock(
  entityType: 'tenant' | 'customer',
  entityId: number,
  configType: string,
): Promise<ConfigInheritanceItem> {
  const resp = await apiClient.get<ConfigInheritanceItem>(`/api/v1/config-inheritance/${entityType}/${entityId}/${configType}`);
  return resp.data;
}

export async function setLockMode(
  entityType: 'tenant' | 'customer',
  entityId: number,
  configType: string,
  lockMode: ConfigInheritanceItem['lockMode'],
): Promise<ConfigInheritanceItem> {
  const resp = await apiClient.put<ConfigInheritanceItem>(`/api/v1/config-inheritance/${entityType}/${entityId}/${configType}`, { lockMode });
  return resp.data;
}

export async function removeLock(entityType: 'tenant' | 'customer', entityId: number, configType: string): Promise<void> {
  await apiClient.delete(`/api/v1/config-inheritance/${entityType}/${entityId}/${configType}`);
}

export async function removeAllLocks(entityType: 'tenant' | 'customer', entityId: number): Promise<void> {
  await apiClient.delete(`/api/v1/config-inheritance/${entityType}/${entityId}`);
}

export async function listEntityGroups(): Promise<EntityGroup[]> {
  const resp = await apiClient.get<EntityGroup[]>('/api/v1/entity-groups');
  return resp.data;
}

export async function createEntityGroup(data: {
  groupName: string;
  groupType: EntityGroup['groupType'];
  description?: string | null;
  tenantId?: number | null;
}): Promise<EntityGroup> {
  const resp = await apiClient.post<EntityGroup>('/api/v1/entity-groups', data);
  return resp.data;
}

export async function updateEntityGroup(
  groupId: number,
  data: {
    groupName?: string;
    groupType?: EntityGroup['groupType'];
    description?: string | null;
    tenantId?: number | null;
  },
): Promise<EntityGroup> {
  const resp = await apiClient.put<EntityGroup>(`/api/v1/entity-groups/${groupId}`, data);
  return resp.data;
}

export async function deleteEntityGroup(groupId: number): Promise<void> {
  await apiClient.delete(`/api/v1/entity-groups/${groupId}`);
}

export async function listGroupMembers(groupId: number): Promise<EntityGroupMember[]> {
  const resp = await apiClient.get<EntityGroupMember[]>(`/api/v1/entity-groups/${groupId}/members`);
  return resp.data;
}

export async function addGroupMember(
  groupId: number,
  entityId: number,
  entityType: EntityGroupMember['entityType'],
): Promise<EntityGroupMember> {
  const resp = await apiClient.post<EntityGroupMember>(`/api/v1/entity-groups/${groupId}/members`, {
    entityId,
    entityType,
  });
  return resp.data;
}

export async function removeGroupMember(
  groupId: number,
  entityId: number,
  entityType: EntityGroupMember['entityType'],
): Promise<void> {
  await apiClient.delete(`/api/v1/entity-groups/${groupId}/members/${entityId}`, {
    params: { entityType },
  });
}

export async function batchAssignLockMode(
  groupId: number,
  configType: string,
  lockMode: ConfigInheritanceItem['lockMode'],
): Promise<void> {
  await apiClient.post(`/api/v1/entity-groups/${groupId}/batch-assign`, {
    configType,
    lockMode,
  });
}
