import apiClient from './api';
import type {
  Customer,
  Device,
  DeviceQuota,
  NetSegmentWeight,
  NotificationConfig,
  PaginatedResponse,
} from '@/types';

/**
 * 客户管理服务
 *
 * 对接 Customer Management Service: 端口 8084
 */
class CustomerService {
  // ---- 客户 CRUD ----

  /** 获取客户列表 (分页) */
  async getCustomers(params: {
    page?: number;
    size?: number;
    sort?: string;
    tenantId?: number;
  }): Promise<PaginatedResponse<Customer>> {
    const response = await apiClient.get<PaginatedResponse<Customer>>(
      '/api/v1/customers',
      { params }
    );
    return response.data;
  }

  /** 搜索客户 */
  async searchCustomers(keyword: string): Promise<Customer[]> {
    const response = await apiClient.get(
      '/api/v1/customers/search',
      { params: { keyword } }
    );
    const data = response.data;
    return Array.isArray(data) ? data : (data?.content ?? []);
  }

  /** 获取客户详情 */
  async getCustomer(customerId: string): Promise<Customer> {
    const response = await apiClient.get<Customer>(
      `/api/v1/customers/${customerId}`
    );
    return response.data;
  }

  /** 获取客户统计 */
  async getCustomerStats(customerId: string): Promise<Record<string, unknown>> {
    const response = await apiClient.get(
      `/api/v1/customers/${customerId}/stats`
    );
    return response.data;
  }

  /** 检查客户是否存在 */
  async customerExists(customerId: string): Promise<boolean> {
    const response = await apiClient.get<boolean>(
      `/api/v1/customers/${customerId}/exists`
    );
    return response.data;
  }

  /** 创建客户 */
  async createCustomer(data: Partial<Customer>): Promise<Customer> {
    const response = await apiClient.post<Customer>(
      '/api/v1/customers',
      data
    );
    return response.data;
  }

  /** 更新客户 */
  async updateCustomer(
    customerId: string,
    data: Partial<Customer>
  ): Promise<Customer> {
    const response = await apiClient.put<Customer>(
      `/api/v1/customers/${customerId}`,
      data
    );
    return response.data;
  }

  /** 部分更新客户 */
  async patchCustomer(
    customerId: string,
    data: Partial<Customer>
  ): Promise<Customer> {
    const response = await apiClient.patch<Customer>(
      `/api/v1/customers/${customerId}`,
      data
    );
    return response.data;
  }

  /** 删除客户 (软删除) */
  async deleteCustomer(customerId: string): Promise<void> {
    await apiClient.delete(`/api/v1/customers/${customerId}`);
  }

  /** 按状态获取客户 */
  async getCustomersByStatus(
    status: string
  ): Promise<PaginatedResponse<Customer>> {
    const response = await apiClient.get<PaginatedResponse<Customer>>(
      `/api/v1/customers/status/${status}`
    );
    return response.data;
  }

  // ---- 设备管理 ----

  /** 获取客户设备列表 */
  async getDevices(customerId: string): Promise<Device[]> {
    const response = await apiClient.get(
      `/api/v1/customers/${customerId}/devices`
    );
    const data = response.data;
    return Array.isArray(data) ? data : (data?.content ?? []);
  }

  /** 获取设备详情 */
  async getDevice(
    customerId: string,
    devSerial: string
  ): Promise<Device> {
    const response = await apiClient.get<Device>(
      `/api/v1/customers/${customerId}/devices/${devSerial}`
    );
    return response.data;
  }

  /** 批量添加设备 */
  async addDevices(
    customerId: string,
    devices: Partial<Device>[]
  ): Promise<Device[]> {
    const response = await apiClient.post<Device[]>(
      `/api/v1/customers/${customerId}/devices/batch`,
      { devices }
    );
    return response.data;
  }

  /** 更新设备 */
  async updateDevice(
    customerId: string,
    devSerial: string,
    data: Partial<Device>
  ): Promise<Device> {
    const response = await apiClient.put<Device>(
      `/api/v1/customers/${customerId}/devices/${devSerial}`,
      data
    );
    return response.data;
  }

  /** 删除设备 */
  async deleteDevice(
    customerId: string,
    devSerial: string
  ): Promise<void> {
    await apiClient.delete(
      `/api/v1/customers/${customerId}/devices/${devSerial}`
    );
  }

  /** 同步设备 */
  async syncDevices(customerId: string): Promise<void> {
    await apiClient.post(
      `/api/v1/customers/${customerId}/devices/sync`
    );
  }

  /** 获取设备配额 */
  async getDeviceQuota(customerId: string): Promise<DeviceQuota> {
    const response = await apiClient.get(
      `/api/v1/customers/${customerId}/devices/quota`
    );
    const raw = response.data as Record<string, unknown>;
    // Backend returns availableDevices (after snake→camel transform); frontend type uses remainingQuota
    if ('availableDevices' in raw && !('remainingQuota' in raw)) {
      raw.remainingQuota = raw.availableDevices;
    }
    // protectedHostCount comes through snake→camel transform automatically
    return raw as unknown as DeviceQuota;
  }

  // ---- 通知配置 ----

  /** 获取通知配置 */
  async getNotificationConfig(
    customerId: string
  ): Promise<NotificationConfig> {
    const response = await apiClient.get<NotificationConfig>(
      `/api/v1/customers/${customerId}/notification-config`
    );
    return response.data;
  }

  /** 更新通知配置 */
  async updateNotificationConfig(
    customerId: string,
    config: Partial<NotificationConfig>
  ): Promise<NotificationConfig> {
    const response = await apiClient.put<NotificationConfig>(
      `/api/v1/customers/${customerId}/notification-config`,
      config
    );
    return response.data;
  }

  // ---- 网段权重 ----

  /** 获取网段权重列表 */
  async getNetWeights(customerId: string): Promise<NetSegmentWeight[]> {
    const response = await apiClient.get<NetSegmentWeight[]>(
      `/api/v1/customers/${customerId}/net-weights`
    );
    return response.data;
  }

  /** 更新网段权重 */
  async updateNetWeight(
    customerId: string,
    weightId: number,
    data: Partial<NetSegmentWeight>
  ): Promise<NetSegmentWeight> {
    const response = await apiClient.put<NetSegmentWeight>(
      `/api/v1/customers/${customerId}/net-weights/${weightId}`,
      data
    );
    return response.data;
  }

  /** 删除网段权重 */
  async deleteNetWeight(
    customerId: string,
    weightId: number
  ): Promise<void> {
    await apiClient.delete(
      `/api/v1/customers/${customerId}/net-weights/${weightId}`
    );
  }
}

export default new CustomerService();
