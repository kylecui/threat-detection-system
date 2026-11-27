import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import i18n from '@/i18n';
import { isAuthenticationFailure, terminateSession } from '@/services/session';

declare module 'axios' {
  export interface AxiosRequestConfig {
    suppressGlobalError?: boolean;
    maxRetries?: number;
    skipScope?: boolean;
  }
}

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
  timeout: 12000,
  headers: {
    'Content-Type': 'application/json',
  },
});

const recentApiErrors = new Map<string, number>();
function notifyApiError(key: string, content: string) {
  const now = Date.now();
  const previous = recentApiErrors.get(key) ?? 0;
  if (now - previous < 2000) return;
  recentApiErrors.set(key, now);
  message.error(content);
  if (recentApiErrors.size > 100) {
    for (const [entryKey, timestamp] of recentApiErrors) {
      if (now - timestamp > 10000) recentApiErrors.delete(entryKey);
    }
  }
}

/**
 * 从 localStorage 获取当前 customerId。
 * 优先级: 统一作用域键 → 固定客户账号 → 兼容旧键。
 * 平台/租户管理员在选择“全部客户”时不再回退到历史 customer_id。
 */
export function getCustomerId(): string | undefined {
  const scoped = localStorage.getItem('scope_customer_id');
  if (scoped) return scoped === '__all__' ? undefined : scoped;
  try {
    const user = JSON.parse(localStorage.getItem('user') || '');
    const roles: string[] = Array.isArray(user?.roles) ? user.roles : [];
    if (roles.includes('SUPER_ADMIN') || roles.includes('TENANT_ADMIN')) {
      return undefined;
    }
  } catch { /* ignore parse errors */ }
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
    const shouldInjectScope = !config.skipScope
      && !skipScopeUrls.some((prefix) => url.includes(prefix));

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
    const scopeCustomerId = getCustomerId();
    if (!config.skipScope && tenantId && config.headers) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
    if (!config.skipScope && scopeCustomerId && config.headers) {
      config.headers['X-Customer-Id'] = scopeCustomerId;
    }

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
    return response;
  },
  (error: AxiosError) => {
    const retryConfig = error.config as (InternalAxiosRequestConfig & {
      _retryCount?: number;
      suppressGlobalError?: boolean;
      maxRetries?: number;
    }) | undefined;
    const status = error.response?.status;
    const safeToRetry = retryConfig?.method?.toUpperCase() === 'GET';
    const transient = !error.response || status === 502 || status === 503 || status === 504;
    const retryCount = retryConfig?._retryCount ?? 0;

    // A single bounded retry masks a brief cold-start race without multiplying
    // every page request into three backend calls on an overloaded node.
    const maxRetries = retryConfig?.maxRetries ?? 1;

    if (retryConfig && safeToRetry && transient && retryCount < maxRetries) {
      retryConfig._retryCount = retryCount + 1;
      const delayMs = retryCount === 0 ? 350 : 900;
      return new Promise((resolve) => window.setTimeout(resolve, delayMs))
        .then(() => apiClient.request(retryConfig));
    }

    const responseData = error.response?.data;
    const hasSession = Boolean(
      localStorage.getItem('token')
      || retryConfig?.headers?.Authorization,
    );
    if (hasSession && isAuthenticationFailure(status, responseData)) {
      if (!retryConfig?.suppressGlobalError) {
        const errorKey = `${status ?? 'auth'}:${retryConfig?.method ?? 'GET'}:${retryConfig?.url ?? 'unknown'}`;
        notifyApiError(errorKey, String(i18n.t('apiErrors.unauthorized')));
      }
      terminateSession('session_expired');
      return Promise.reject(error);
    }

    if (retryConfig?.suppressGlobalError) {
      return Promise.reject(error);
    }

    const t = i18n.t.bind(i18n);
    const errorKey = `${status ?? 'network'}:${retryConfig?.method ?? 'GET'}:${retryConfig?.url ?? 'unknown'}`;
    if (error.response) {
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          notifyApiError(errorKey, String(t('apiErrors.badRequest', { message: (data as any)?.message || t('apiErrors.defaultBadRequest') })));
          break;
        case 401:
          notifyApiError(errorKey, String(t('apiErrors.unauthorized')));
          break;
        case 403:
          notifyApiError(errorKey, String(t('apiErrors.forbidden')));
          break;
        case 404:
          notifyApiError(errorKey, String(t('apiErrors.notFound')));
          break;
        case 500:
          notifyApiError(errorKey, String(t('apiErrors.serverError', { message: (data as any)?.message || t('apiErrors.defaultServerError') })));
          break;
        case 503:
          notifyApiError(errorKey, String(t('apiErrors.serviceUnavailable')));
          break;
        default:
          notifyApiError(errorKey, String(t('apiErrors.requestFailed', { status })));
      }
    } else if (error.request) {
      notifyApiError(errorKey, String(t('apiErrors.networkError')));
    } else {
      notifyApiError(errorKey, String(t('apiErrors.configError', { message: error.message })));
    }

    return Promise.reject(error);
  }
);

export default apiClient;
