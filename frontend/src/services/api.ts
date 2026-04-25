import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';

function snakeToCamel(str: string): string {
  return str.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
}

function convertKeys(obj: unknown): unknown {
  if (Array.isArray(obj)) {
    return obj.map(convertKeys);
  }
  if (obj !== null && typeof obj === 'object' && !(obj instanceof Date)) {
    return Object.entries(obj as Record<string, unknown>).reduce(
      (acc, [key, value]) => {
        acc[snakeToCamel(key)] = convertKeys(value);
        return acc;
      },
      {} as Record<string, unknown>,
    );
  }
  return obj;
}

const apiClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 从 localStorage 获取当前 customerId。
 * 优先级: customer_id 键 → user JSON 中的 customerId 字段。
 * 管理员用户无绑定客户时返回 undefined（不注入 customer_id 参数）。
 */
export function getCustomerId(): string | undefined {
  const stored = localStorage.getItem('customer_id');
  if (stored) return stored;
  try {
    const user = JSON.parse(localStorage.getItem('user') || '');
    if (user?.customerId) return user.customerId;
  } catch { /* ignore parse errors */ }
  return undefined;
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

    const url = config.url || '';
    const skipScopeUrls = [
      '/api/v1/auth/',
      '/api/v1/system-config',
      '/api/v1/tenants',
      '/api/v1/users',
      '/tenant',
      '/api/v1/tire-plugins',
      '/api/v1/llm-providers',
      '/api/v1/config-assignments',
      '/api/v1/user-config',
      '/api/v1/system/health',
    ];
    const shouldInjectScope = !skipScopeUrls.some((prefix) => url.includes(prefix));

    if (shouldInjectScope) {
      const customerId = getCustomerId();
      if (customerId) {
        if (config.params) {
          config.params.customer_id = customerId;
        } else {
          config.params = { customer_id: customerId };
        }
      }
    }

    const tenantId = localStorage.getItem('scope_tenant_id') || localStorage.getItem('tenant_id');
    const scopeCustomerId = localStorage.getItem('scope_customer_id') || localStorage.getItem('customer_id');
    if (tenantId && config.headers) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
    if (scopeCustomerId && scopeCustomerId !== '__all__' && config.headers) {
      config.headers['X-Customer-Id'] = scopeCustomerId;
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
    if (response.data && typeof response.data === 'object') {
      response.data = convertKeys(response.data);
    }
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
