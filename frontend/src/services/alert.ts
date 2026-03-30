import apiClient from './api';
import type {
  Alert,
  AlertQueryFilter,
  AlertAnalytics,
  NotificationAnalytics,
  EscalationAnalytics,
  PaginatedResponse,
} from '@/types';

/**
 * 告警管理服务
 *
 * 对接 Alert Management Service: 端口 8082
 */
class AlertService {
  /** 获取告警列表 (分页) */
  async getAlerts(filter: AlertQueryFilter): Promise<PaginatedResponse<Alert>> {
    const response = await apiClient.get<PaginatedResponse<Alert>>(
      '/api/v1/alerts',
      { params: filter }
    );
    return response.data;
  }

  /** 获取告警详情 */
  async getAlert(id: number): Promise<Alert> {
    const response = await apiClient.get<Alert>(`/api/v1/alerts/${id}`);
    return response.data;
  }

  /** 更新告警状态 */
  async updateStatus(id: number, status: string): Promise<Alert> {
    const response = await apiClient.put<Alert>(
      `/api/v1/alerts/${id}/status`,
      null,
      { params: { status } }
    );
    return response.data;
  }

  /** 解决告警 */
  async resolve(
    id: number,
    data: { resolution: string; resolvedBy: string }
  ): Promise<Alert> {
    const response = await apiClient.post<Alert>(
      `/api/v1/alerts/${id}/resolve`,
      data
    );
    return response.data;
  }

  /** 分配告警 */
  async assign(id: number, assignedTo: string): Promise<Alert> {
    const response = await apiClient.post<Alert>(
      `/api/v1/alerts/${id}/assign`,
      { assignedTo }
    );
    return response.data;
  }

  /** 升级告警 */
  async escalate(id: number, reason: string): Promise<Alert> {
    const response = await apiClient.post<Alert>(
      `/api/v1/alerts/${id}/escalate`,
      { reason }
    );
    return response.data;
  }

  /** 取消升级 */
  async cancelEscalation(id: number): Promise<Alert> {
    const response = await apiClient.post<Alert>(
      `/api/v1/alerts/${id}/cancel-escalation`
    );
    return response.data;
  }

  /** 归档告警 */
  async archive(params: {
    beforeDate?: string;
    status?: string;
  }): Promise<{ archivedCount: number }> {
    const response = await apiClient.post<{ archivedCount: number }>(
      '/api/v1/alerts/archive',
      null,
      { params }
    );
    return response.data;
  }

  /** 获取告警分析 */
  async getAnalytics(): Promise<AlertAnalytics> {
    const response = await apiClient.get<AlertAnalytics>(
      '/api/v1/alerts/analytics'
    );
    return response.data;
  }

  /** 获取通知分析 */
  async getNotificationAnalytics(): Promise<NotificationAnalytics> {
    const response = await apiClient.get<NotificationAnalytics>(
      '/api/v1/alerts/notifications/analytics'
    );
    return response.data;
  }

  /** 获取升级分析 */
  async getEscalationAnalytics(): Promise<EscalationAnalytics> {
    const response = await apiClient.get<EscalationAnalytics>(
      '/api/v1/alerts/escalations/analytics'
    );
    return response.data;
  }

  /** 发送邮件通知 */
  async sendEmailNotification(data: {
    alertId: number;
    recipients: string[];
  }): Promise<void> {
    await apiClient.post('/api/v1/alerts/notify/email', data);
  }
}

export default new AlertService();
