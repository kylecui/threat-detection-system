import apiClient from './api';
import type { PipelineHealthResponse } from '@/types';

class SystemService {
  async getHealth(): Promise<PipelineHealthResponse> {
    const response = await apiClient.get<PipelineHealthResponse>(
      '/api/v1/system/health',
      { timeout: 10000 },
    );
    return response.data;
  }
}

export default new SystemService();
