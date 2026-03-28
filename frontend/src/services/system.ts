import apiClient from './api';
import type { ServiceHealth, DataIngestionStats } from '@/types';

/**
 * 系统监控服务
 *
 * 聚合所有微服务的健康检查
 */

interface ServiceEndpoint {
  name: string;
  port: number;
  healthPath: string;
}

/** 所有微服务端点 */
const SERVICES: ServiceEndpoint[] = [
  { name: '数据摄取', port: 8080, healthPath: '/actuator/health' },
  { name: '流处理 (Flink)', port: 8081, healthPath: '/overview' },
  { name: '告警管理', port: 8082, healthPath: '/actuator/health' },
  { name: '威胁评估', port: 8083, healthPath: '/api/v1/assessment/health' },
  { name: '客户管理', port: 8084, healthPath: '/actuator/health' },
  { name: '威胁情报', port: 8085, healthPath: '/actuator/health' },
  { name: 'ML检测', port: 8086, healthPath: '/health' },
  { name: 'API网关', port: 8888, healthPath: '/actuator/health' },
  { name: '配置服务器', port: 8899, healthPath: '/actuator/health' },
];

class SystemService {
  /**
   * 检查单个服务健康状态
   * 通过API网关代理请求
   */
  private async checkService(
    service: ServiceEndpoint
  ): Promise<ServiceHealth> {
    const start = Date.now();
    try {
      const response = await apiClient.get(service.healthPath, {
        timeout: 5000,
      });
      const responseTime = Date.now() - start;
      return {
        name: service.name,
        port: service.port,
        status: 'UP',
        responseTime,
        details: response.data,
        lastChecked: new Date().toISOString(),
      };
    } catch {
      const responseTime = Date.now() - start;
      return {
        name: service.name,
        port: service.port,
        status: 'DOWN',
        responseTime,
        lastChecked: new Date().toISOString(),
      };
    }
  }

  /** 获取所有服务健康状态 */
  async getAllServiceHealth(): Promise<ServiceHealth[]> {
    const promises = SERVICES.map((svc) => this.checkService(svc));
    return Promise.all(promises);
  }

  /** 获取数据摄取统计 */
  async getDataIngestionStats(): Promise<DataIngestionStats> {
    const response = await apiClient.get<DataIngestionStats>(
      '/api/v1/logs/stats'
    );
    return response.data;
  }
}

export default new SystemService();
