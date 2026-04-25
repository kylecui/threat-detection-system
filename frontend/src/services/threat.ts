import apiClient from './api';
import type {
  ThreatAssessment,
  Statistics,
  Customer,
  PaginatedResponse,
  ThreatQueryFilter,
  ChartDataPoint,
  TopAttacker,
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
      `/api/v1/assessment/${id}`
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
  async getThreatTrend(customerId: string, hours: number = 24): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/trend',
      {
        params: {
          customer_id: customerId,
          hours,
        },
      }
    );
    return response.data;
  }

  /**
   * 获取端口分布
   */
  async getPortDistribution(customerId: string, hours: number = 24): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/port-distribution',
      {
        params: {
          customer_id: customerId,
          hours,
        },
      }
    );
    return response.data;
  }

  /**
   * 获取Top攻击者 (后端聚合)
   */
  async getTopAttackers(customerId: string, limit: number = 10, hours: number = 24): Promise<TopAttacker[]> {
    const response = await apiClient.get<TopAttacker[]>(
      '/api/v1/assessment/top-attackers',
      { params: { customer_id: customerId, limit, hours } }
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

  async getTenantTrend(customerIds: string[], hours: number = 24): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/trend/tenant',
      { params: { customer_ids: customerIds.join(','), hours } }
    );
    return response.data;
  }

  async getTenantPortDistribution(customerIds: string[], hours: number = 24): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ChartDataPoint[]>(
      '/api/v1/assessment/port-distribution/tenant',
      { params: { customer_ids: customerIds.join(','), hours } }
    );
    return response.data;
  }

  /**
   * 删除威胁记录 (管理员功能)
   * TODO: Backend AssessmentController has no DELETE endpoint yet — this will 404 until implemented
   */
  async deleteThreat(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/assessment/${id}`);
  }

  /**
   * 批量删除威胁记录
   * TODO: Backend AssessmentController has no batch-delete endpoint yet — this will 404 until implemented
   */
  async batchDeleteThreats(ids: number[]): Promise<void> {
    await apiClient.post('/api/v1/assessment/batch-delete', { ids });
  }

  /**
   * 导出当前页威胁数据为CSV字符串
   */
  exportThreatsToCsv(threats: ThreatAssessment[]): string {
    const headers = [
      'id',
      'customerId',
      'assessmentTime',
      'attackMac',
      'attackIp',
      'threatLevel',
      'threatScore',
      'attackCount',
      'uniqueIps',
      'uniquePorts',
      'uniqueDevices',
      'portList',
      'portRiskScore',
      'detectionTier',
      'createdAt',
      'mitigationRecommendations',
    ];

    const escape = (value: unknown): string => {
      const text = String(value ?? '');
      if (text.includes('"') || text.includes(',') || text.includes('\n')) {
        return `"${text.replace(/"/g, '""')}"`;
      }
      return text;
    };

    const rows = threats.map((item) => [
      item.id,
      item.customerId,
      item.assessmentTime,
      item.attackMac,
      item.attackIp ?? '',
      item.threatLevel,
      item.threatScore,
      item.attackCount,
      item.uniqueIps,
      item.uniquePorts,
      item.uniqueDevices,
      item.portList ?? '',
      item.portRiskScore ?? '',
      item.detectionTier ?? '',
      item.createdAt,
      item.mitigationRecommendations?.join('; ') ?? '',
    ]);

    const lines = [headers.join(','), ...rows.map((row) => row.map((cell) => escape(cell)).join(','))];
    return lines.join('\n');
  }

  /**
   * 导出当前页威胁数据为JSON字符串
   */
  exportThreatsToJson(threats: ThreatAssessment[]): string {
    return JSON.stringify(threats, null, 2);
  }
}

export default new ThreatService();
