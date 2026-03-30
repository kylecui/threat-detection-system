import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { REGION_ENDPOINTS, type RegionId } from '@/types';

/**
 * 区域路由优先级: localStorage.region → VITE_API_BASE_URL → /api
 */
function getRegionBaseURL(): string {
  const regionId = (localStorage.getItem('region') || 'auto') as RegionId;
  const regionConfig = REGION_ENDPOINTS[regionId];

  // 'auto' 或空 apiBase → 回退到环境变量或默认值
  if (!regionConfig || !regionConfig.apiBase) {
    const envBase = import.meta.env.VITE_API_BASE_URL;
    return envBase !== undefined && envBase !== null ? envBase : '/api';
  }
  return regionConfig.apiBase;
}

/**
 * Axios实例配置
 * 
 * API网关地址:
 * - 开发环境: http://localhost:8888
 * - 生产环境: /api (Nginx代理)
 * - 多区域: https://{region}.threat-detection.io
 */
const apiClient: AxiosInstance = axios.create({
  baseURL: getRegionBaseURL(),
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export function switchRegion(regionId: RegionId): void {
  localStorage.setItem('region', regionId);
  apiClient.defaults.baseURL = getRegionBaseURL();
}

/**
 * 请求拦截器
 */
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Skip customer_id injection for auth, system-config, tenant, and user management endpoints
    const url = config.url || '';
    if (!url.includes('/api/v1/auth/') && !url.includes('/api/v1/system-config')
        && !url.includes('/api/v1/tenants') && !url.includes('/api/v1/users')
        && !url.includes('/tenant')) {
      const customerId = localStorage.getItem('customer_id') || 'demo-customer';
      if (config.params) {
        config.params.customer_id = customerId;
      } else {
        config.params = { customer_id: customerId };
      }
    }

    console.log(`[API Request] ${config.method?.toUpperCase()} ${config.url}`, config.params);
    return config;
  },
  (error: AxiosError) => {
    console.error('[API Request Error]', error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器
 */
apiClient.interceptors.response.use(
  (response) => {
    console.log(`[API Response] ${response.config.url}`, response.data);
    return response;
  },
  (error: AxiosError) => {
    console.error('[API Response Error]', error);

    // 处理不同的错误状态码
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          message.error(`请求错误: ${(data as any)?.message || '参数错误'}`);
          break;
        case 401:
          message.error('未授权，请重新登录');
          // 清除token并跳转登录页
          localStorage.removeItem('token');
          window.location.href = '/login';
          break;
        case 403:
          message.error('没有权限访问该资源');
          break;
        case 404:
          message.error('请求的资源不存在');
          break;
        case 500:
          message.error(`服务器错误: ${(data as any)?.message || '内部错误'}`);
          break;
        case 503:
          message.error('服务暂时不可用，请稍后重试');
          break;
        default:
          message.error(`请求失败 (${status})`);
      }
    } else if (error.request) {
      message.error('网络错误，请检查网络连接');
    } else {
      message.error(`请求配置错误: ${error.message}`);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
