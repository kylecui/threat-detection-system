import apiClient from './api';
import type {
  ThreatAssessment,
  Statistics,
  Customer,
  PaginatedResponse,
  ThreatQueryFilter,
  ChartDataPoint,
} from '@/types';

/**
 * 威胁评估服务
 * 
 * 对接API Gateway: http://localhost:8888
 */
class ThreatService {
  /**
   * 获取威胁列表 (分页)
   */
  async getThreatList(
    filter: ThreatQueryFilter
  ): Promise<PaginatedResponse<ThreatAssessment>> {
    const response = await apiClient.get<PaginatedResponse<ThreatAssessment>>(
      '/api/v1/assessment/assessments',
      { params: filter }
    );
    return response.data;
  }

  /**
   * 获取威胁详情
   */
  async getThreatDetail(id: number): Promise<ThreatAssessment> {
    const response = await apiClient.get<ThreatAssessment>(
      `/api/v1/assessment/assessments/${id}`
    );
    return response.data;
  }

  /**
   * 获取统计数据
   */
  async getStatistics(customerId: string): Promise<Statistics> {
    const response = await apiClient.get<Statistics>(
      '/api/v1/assessment/statistics',
      { params: { customer_id: customerId } }
    );
    return response.data;
  }

  /**
   * 获取威胁趋势数据 (24小时)
   */
  async getThreatTrend(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/trend',
      {
        params: {
          customer_id: customerId,
          hours: 24,
        },
      }
    );
    return response.data;
  }

  /**
   * 获取端口分布
   */
  async getPortDistribution(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/port-distribution',
      {
        params: {
          customer_id: customerId,
        },
      }
    );
    return response.data;
  }

  async getCustomersByTenant(tenantId: number): Promise<Customer[]> {
    const response = await apiClient.get<Customer[]>(
      `/api/v1/customers/by-tenant/${tenantId}`
    );
    return response.data;
  }

  async getTenantStatistics(customerIds: string[]): Promise<Statistics> {
    const response = await apiClient.get<Statistics>(
      '/api/v1/assessment/statistics/tenant',
      { params: { customer_ids: customerIds.join(',') } }
    );
    return response.data;
  }

  async getTenantThreatList(
    customerIds: string[],
    params: { page: number; page_size: number }
  ): Promise<PaginatedResponse<ThreatAssessment>> {
    const response = await apiClient.get<PaginatedResponse<ThreatAssessment>>(
      '/api/v1/assessment/assessments/tenant',
      { params: { customer_ids: customerIds.join(','), ...params } }
    );
    return response.data;
  }

  async getTenantTrend(customerIds: string[]): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/trend/tenant',
      { params: { customer_ids: customerIds.join(',') } }
    );
    return response.data;
  }

  async getTenantPortDistribution(customerIds: string[]): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/port-distribution/tenant',
      { params: { customer_ids: customerIds.join(',') } }
    );
    return response.data;
  }

  /**
   * 删除威胁记录 (管理员功能)
   */
  async deleteThreat(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/assessment/assessments/${id}`);
  }

  /**
   * 批量删除威胁记录
   */
  async batchDeleteThreats(ids: number[]): Promise<void> {
    await apiClient.post('/api/v1/assessment/assessments/batch-delete', { ids });
  }
}

export default new ThreatService();
