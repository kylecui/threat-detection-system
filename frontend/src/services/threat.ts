import apiClient from './api';
import type {
  ThreatAssessment,
  Statistics,
  PaginatedResponse,
  ThreatQueryFilter,
  ApiResponse,
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
    const response = await apiClient.get<ApiResponse<PaginatedResponse<ThreatAssessment>>>(
      '/api/v1/assessment/assessments',
      { params: filter }
    );
    return response.data.data;
  }

  /**
   * 获取威胁详情
   */
  async getThreatDetail(id: number): Promise<ThreatAssessment> {
    const response = await apiClient.get<ApiResponse<ThreatAssessment>>(
      `/api/v1/assessment/assessments/${id}`
    );
    return response.data.data;
  }

  /**
   * 获取统计数据
   */
  async getStatistics(customerId: string): Promise<Statistics> {
    const response = await apiClient.get<ApiResponse<Statistics>>(
      '/api/v1/assessment/statistics',
      { params: { customer_id: customerId } }
    );
    return response.data.data;
  }

  /**
   * 获取威胁趋势数据 (24小时)
   */
  async getThreatTrend(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ApiResponse<ChartDataPoint[]>>(
      '/api/v1/assessment/trend',
      {
        params: {
          customer_id: customerId,
          hours: 24,
        },
      }
    );
    return response.data.data;
  }

  /**
   * 获取Top攻击者
   */
  async getTopAttackers(customerId: string, limit: number = 10): Promise<any[]> {
    const response = await apiClient.get<ApiResponse<any[]>>(
      '/api/v1/assessment/top-attackers',
      {
        params: {
          customer_id: customerId,
          limit,
        },
      }
    );
    return response.data.data;
  }

  /**
   * 获取端口分布
   */
  async getPortDistribution(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<ApiResponse<ChartDataPoint[]>>(
      '/api/v1/assessment/port-distribution',
      {
        params: {
          customer_id: customerId,
        },
      }
    );
    return response.data.data;
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
