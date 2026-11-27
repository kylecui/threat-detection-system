import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
  type AxiosInstance,
} from 'axios';
import { beforeEach, describe, expect, test, vi } from 'vitest';

type RequestFulfilled = (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig | Promise<InternalAxiosRequestConfig>;
type RequestRejected = (error: AxiosError) => Promise<never>;
type ResponseFulfilled = (response: AxiosResponse) => AxiosResponse | Promise<AxiosResponse>;
type ResponseRejected = (error: AxiosError) => Promise<never>;

const hoisted = vi.hoisted(() => {
  let requestFulfilled: RequestFulfilled | undefined;
  let requestRejected: RequestRejected | undefined;
  let responseFulfilled: ResponseFulfilled | undefined;
  let responseRejected: ResponseRejected | undefined;

  const createMock = vi.fn();
  const messageErrorMock = vi.fn();

  const mockInstance = {
    interceptors: {
      request: {
        use: vi.fn((onFulfilled: RequestFulfilled, onRejected?: RequestRejected) => {
          requestFulfilled = onFulfilled;
          requestRejected = onRejected;
          return 0;
        }),
      },
      response: {
        use: vi.fn((onFulfilled: ResponseFulfilled, onRejected?: ResponseRejected) => {
          responseFulfilled = onFulfilled;
          responseRejected = onRejected;
          return 0;
        }),
      },
    },
  } as unknown as AxiosInstance;

  createMock.mockReturnValue(mockInstance);

  return {
    createMock,
    messageErrorMock,
    getRequestFulfilled: () => requestFulfilled,
    getRequestRejected: () => requestRejected,
    getResponseFulfilled: () => responseFulfilled,
    getResponseRejected: () => responseRejected,
  };
});

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios');
  return {
    ...actual,
    default: {
      ...actual.default,
      create: hoisted.createMock,
    },
    create: hoisted.createMock,
  };
});

vi.mock('antd', () => ({
  message: {
    error: hoisted.messageErrorMock,
  },
}));

import apiClient, { getCustomerId } from '@/services/api';
import i18n from '@/i18n';
import { resetSessionRedirectGuardForTests } from '@/services/session';

function buildConfig(url: string): InternalAxiosRequestConfig {
  return {
    url,
    method: 'get',
    headers: new AxiosHeaders(),
  } as InternalAxiosRequestConfig;
}

describe('api service', () => {
  beforeEach(async () => {
    localStorage.clear();
    resetSessionRedirectGuardForTests();
    await i18n.changeLanguage('zh-CN');
    hoisted.messageErrorMock.mockReset();
    vi.restoreAllMocks();
  });

  test('creates axios instance with expected base config', () => {
    expect(apiClient).toBeDefined();
    expect(hoisted.createMock).toHaveBeenCalledWith({
      baseURL: '/api',
      timeout: 12000,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  });

  test('injects auth token, scope headers, and customer_id param', async () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('customer_id', 'cust-abc');
    localStorage.setItem('scope_tenant_id', '888');
    localStorage.setItem('scope_customer_id', 'cust-scope');

    const handler = hoisted.getRequestFulfilled();
    expect(handler).toBeDefined();

    const config = buildConfig('/api/v1/alerts');
    const output = await handler!(config);

    expect(output.headers.Authorization).toBe('Bearer jwt-token');
    expect(output.headers['X-Tenant-Id']).toBe('888');
    expect(output.headers['X-Customer-Id']).toBe('cust-scope');
    expect(output.params).toEqual({ customer_id: 'cust-scope' });
  });

  test('does not inject customer scope params for skipped URLs', async () => {
    localStorage.setItem('customer_id', 'cust-should-not-inject');

    const handler = hoisted.getRequestFulfilled();
    expect(handler).toBeDefined();

    const config = buildConfig('/api/v1/auth/login');
    const output = await handler!(config);

    expect(output.params).toBeUndefined();
  });

  test('getCustomerId falls back to user payload when scope and legacy keys are absent', () => {
    localStorage.setItem('user', JSON.stringify({ customerId: 'customer-from-user' }));
    expect(getCustomerId()).toBe('customer-from-user');
  });

  test('administrator all-customer scope never falls back to a stale legacy customer', () => {
    localStorage.setItem('customer_id', 'stale-customer');
    localStorage.setItem('user', JSON.stringify({ roles: ['TENANT_ADMIN'] }));
    expect(getCustomerId()).toBeUndefined();
  });

  test('skipScope omits both tenant and customer scope headers', async () => {
    localStorage.setItem('scope_tenant_id', '5');
    localStorage.setItem('scope_customer_id', 'cust-5');
    const config = buildConfig('/api/v1/tenants');
    config.skipScope = true;
    const output = await hoisted.getRequestFulfilled()!(config);
    expect(output.headers['X-Tenant-Id']).toBeUndefined();
    expect(output.headers['X-Customer-Id']).toBeUndefined();
    expect(output.params).toBeUndefined();
  });

  test('response success handler converts snake_case keys recursively', async () => {
    const successHandler = hoisted.getResponseFulfilled();
    expect(successHandler).toBeDefined();

    const response: AxiosResponse = {
      data: {
        top_level_key: 1,
        nested_object: {
          child_key: 'x',
        },
        list_items: [{ list_value: 2 }],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: buildConfig('/api/v1/alerts'),
    };

    const transformed = await successHandler!(response);

    expect(transformed.data).toEqual({
      topLevelKey: 1,
      nestedObject: {
        childKey: 'x',
      },
      listItems: [{ listValue: 2 }],
    });
  });

  test('error response handler handles 401 by clearing token and redirecting', async () => {
    localStorage.setItem('token', 'token-before-401');

    const errorHandler = hoisted.getResponseRejected();
    expect(errorHandler).toBeDefined();

    const config = buildConfig('/api/v1/alerts');
    const response: AxiosResponse = {
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
      data: { message: 'Unauthorized' },
    };
    const error = new AxiosError('Unauthorized', '401', config, {}, response);

    await expect(errorHandler!(error)).rejects.toBe(error);
    expect(hoisted.messageErrorMock).toHaveBeenCalledWith('未授权，请重新登录');
    expect(localStorage.getItem('token')).toBeNull();
  });

  test('suppressed requests still terminate an expired session and clear scope state', async () => {
    for (const key of [
      'token', 'refreshToken', 'user', 'customer_id', 'tenant_id',
      'scope_tenant_id', 'scope_customer_id',
    ]) {
      localStorage.setItem(key, 'value');
    }
    const config = buildConfig('/api/v1/tenants/search');
    config.suppressGlobalError = true;
    const response: AxiosResponse = {
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
      data: { code: 'TOKEN_EXPIRED', message: 'Token has expired' },
    };
    const error = new AxiosError('Unauthorized', '401', config, {}, response);

    await expect(hoisted.getResponseRejected()!(error)).rejects.toBe(error);

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('scope_tenant_id')).toBeNull();
    expect(localStorage.getItem('scope_customer_id')).toBeNull();
    expect(hoisted.messageErrorMock).not.toHaveBeenCalled();
  });

  test('ordinary 403 permission errors do not terminate the session', async () => {
    localStorage.setItem('token', 'valid-token');
    const config = buildConfig('/api/v1/users');
    const response: AxiosResponse = {
      status: 403,
      statusText: 'Forbidden',
      headers: {},
      config,
      data: { code: 'ACCESS_DENIED', message: 'Insufficient role' },
    };
    const error = new AxiosError('Forbidden', '403', config, {}, response);

    await expect(hoisted.getResponseRejected()!(error)).rejects.toBe(error);

    expect(localStorage.getItem('token')).toBe('valid-token');
    expect(hoisted.messageErrorMock).toHaveBeenCalledWith('没有权限访问该资源');
  });
});
