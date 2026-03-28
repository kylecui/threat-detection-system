import apiClient from './api';
import type {
  ThreatIndicator,
  ThreatFeed,
  IpLookupResult,
  IntelStatistics,
  PaginatedResponse,
} from '@/types';

/**
 * 威胁情报服务
 *
 * 对接 Threat Intelligence Service: 端口 8085
 */
class IntelService {
  /** IP信誉查询 */
  async lookup(ip: string): Promise<IpLookupResult> {
    const response = await apiClient.get<IpLookupResult>(
      '/api/v1/threat-intel/lookup',
      { params: { ip } }
    );
    return response.data;
  }

  /** 获取指标列表 (分页) */
  async getIndicators(params: {
    page?: number;
    size?: number;
    iocType?: string;
    severity?: string;
    sourceName?: string;
  }): Promise<PaginatedResponse<ThreatIndicator>> {
    const response = await apiClient.get<PaginatedResponse<ThreatIndicator>>(
      '/api/v1/threat-intel/indicators',
      { params }
    );
    return response.data;
  }

  /** 获取指标详情 */
  async getIndicator(id: number): Promise<ThreatIndicator> {
    const response = await apiClient.get<ThreatIndicator>(
      `/api/v1/threat-intel/indicators/${id}`
    );
    return response.data;
  }

  /** 创建指标 */
  async createIndicator(
    data: Partial<ThreatIndicator>
  ): Promise<ThreatIndicator> {
    const response = await apiClient.post<ThreatIndicator>(
      '/api/v1/threat-intel/indicators',
      data
    );
    return response.data;
  }

  /** 更新指标 */
  async updateIndicator(
    id: number,
    data: Partial<ThreatIndicator>
  ): Promise<ThreatIndicator> {
    const response = await apiClient.put<ThreatIndicator>(
      `/api/v1/threat-intel/indicators/${id}`,
      data
    );
    return response.data;
  }

  /** 删除指标 */
  async deleteIndicator(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/threat-intel/indicators/${id}`);
  }

  /** 批量导入指标 */
  async bulkCreateIndicators(
    indicators: Partial<ThreatIndicator>[]
  ): Promise<ThreatIndicator[]> {
    const response = await apiClient.post<ThreatIndicator[]>(
      '/api/v1/threat-intel/indicators/bulk',
      indicators
    );
    return response.data;
  }

  /** 添加目击记录 */
  async addSighting(id: number): Promise<ThreatIndicator> {
    const response = await apiClient.post<ThreatIndicator>(
      `/api/v1/threat-intel/indicators/${id}/sighting`
    );
    return response.data;
  }

  /** 获取Feed列表 */
  async getFeeds(): Promise<ThreatFeed[]> {
    const response = await apiClient.get<ThreatFeed[]>(
      '/api/v1/threat-intel/feeds'
    );
    return response.data;
  }

  /** 更新Feed */
  async updateFeed(
    id: number,
    data: Partial<ThreatFeed>
  ): Promise<ThreatFeed> {
    const response = await apiClient.put<ThreatFeed>(
      `/api/v1/threat-intel/feeds/${id}`,
      data
    );
    return response.data;
  }

  /** 手动触发Feed轮询 */
  async pollFeed(id: number): Promise<{ polledCount: number }> {
    const response = await apiClient.post<{ polledCount: number }>(
      `/api/v1/threat-intel/feeds/${id}/poll`
    );
    return response.data;
  }

  /** 获取统计数据 */
  async getStatistics(): Promise<IntelStatistics> {
    const response = await apiClient.get<IntelStatistics>(
      '/api/v1/threat-intel/statistics'
    );
    return response.data;
  }
}

export default new IntelService();
