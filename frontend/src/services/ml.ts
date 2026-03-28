import apiClient from './api';
import type {
  MlHealthStatus,
  MlModelInfo,
  MlReloadResult,
  MlBufferStats,
  MlDriftStatus,
  MlShadowStats,
} from '@/types';

/**
 * ML检测服务
 *
 * 对接 ML Detection Service: 端口 8086
 */
class MlService {
  /** 获取健康状态 */
  async getHealth(): Promise<MlHealthStatus> {
    const response = await apiClient.get<MlHealthStatus>(
      '/api/v1/ml/health'
    );
    return response.data;
  }

  /** 获取模型列表 */
  async getModels(): Promise<MlModelInfo[]> {
    const response = await apiClient.get<MlModelInfo[]>(
      '/api/v1/ml/models'
    );
    return response.data;
  }

  /** 重载模型 */
  async reloadModels(): Promise<MlReloadResult> {
    const response = await apiClient.post<MlReloadResult>(
      '/api/v1/ml/models/reload'
    );
    return response.data;
  }

  /** 获取序列缓冲区统计 */
  async getBufferStats(): Promise<MlBufferStats> {
    const response = await apiClient.get<MlBufferStats>(
      '/api/v1/ml/buffer/stats'
    );
    return response.data;
  }

  /** 获取漂移状态 */
  async getDriftStatus(): Promise<MlDriftStatus> {
    const response = await apiClient.get<MlDriftStatus>(
      '/api/v1/ml/drift/status'
    );
    return response.data;
  }

  /** 获取影子评分统计 */
  async getShadowStats(): Promise<MlShadowStats> {
    const response = await apiClient.get<MlShadowStats>(
      '/api/v1/ml/shadow/stats'
    );
    return response.data;
  }
}

export default new MlService();
