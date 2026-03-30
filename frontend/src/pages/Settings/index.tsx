import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Button,
  Switch,
  message,
  Tabs,
  Space,
  Divider,
  Select,
  Tag,
  Descriptions,
  Alert,
  Row,
  Col,
  Spin,
  Typography,
  Table,
  Modal,
} from 'antd';
import {
  UserOutlined,
  MailOutlined,
  SettingOutlined,
  BellOutlined,
  AppstoreOutlined,
  SaveOutlined,
  ReloadOutlined,
  GlobalOutlined,
  ApiOutlined,
  RobotOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import apiClient, { switchRegion } from '@/services/api';
import { getConfigsByCategory, batchUpdateConfigs, validateLlmConnection } from '@/services/config';
import type { LlmValidateResult as ConfigLlmValidateResult } from '@/services/config';
import {
  listTirePlugins,
  createTirePlugin,
  updateTirePlugin,
  deleteTirePlugin,
  listLlmProviders,
  createLlmProvider,
  updateLlmProvider,
  deleteLlmProvider,
  validateLlmProvider,
  listConfigAssignments,
  getConfigAssignment,
  assignConfig,
  unassignConfig,
  getUserConfig,
  saveUserConfig,
  getResolvedConfig,
} from '@/services/tire';
import type { ColumnsType } from 'antd/es/table';
import type {
  TireCustomPlugin,
  LlmProvider,
  ConfigAssignment,
  LlmValidateResult,
  UserConfig,
  ResolvedConfig,
} from '@/services/tire';
import type { SmtpConfig, NotificationConfig, RegionId, SystemConfig } from '@/types';
import { REGION_ENDPOINTS } from '@/types';

const { Text } = Typography;

const TI_API_KEY_NAMES = [
  'ABUSEIPDB_API_KEY',
  'GREYNOISE_API_KEY',
  'OTX_API_KEY',
  'SHODAN_API_KEY',
  'THREATBOOK_API_KEY',
  'TIANJIYOUMENG_API_KEY',
  'VT_API_KEY',
] as const;

const Settings = () => {
  const [basicForm] = Form.useForm();
  const [smtpForm] = Form.useForm();
  const [notifForm] = Form.useForm();
  const [tireForm] = Form.useForm();
  const [pluginForm] = Form.useForm();
  const [llmForm] = Form.useForm();
  const [customPluginForm] = Form.useForm();
  const [llmProviderForm] = Form.useForm();
  const [assignmentForm] = Form.useForm();
  const [userConfigForm] = Form.useForm();

  const [smtpConfig, setSmtpConfig] = useState<SmtpConfig | null>(null);
  const [, setNotifConfig] = useState<NotificationConfig | null>(null);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [notifLoading, setNotifLoading] = useState(false);

  const [tireConfigs, setTireConfigs] = useState<SystemConfig[]>([]);
  const [llmConfigs, setLlmConfigs] = useState<SystemConfig[]>([]);
  const [tireGeneralConfigs, setTireGeneralConfigs] = useState<SystemConfig[]>([]);
  const [pluginConfigs, setPluginConfigs] = useState<SystemConfig[]>([]);
  const [tireLoading, setTireLoading] = useState(false);
  const [pluginLoading, setPluginLoading] = useState(false);
  const [llmLoading, setLlmLoading] = useState(false);
  const [tireSaving, setTireSaving] = useState(false);
  const [pluginSaving, setPluginSaving] = useState(false);
  const [llmSaving, setLlmSaving] = useState(false);
  const [llmValidating, setLlmValidating] = useState(false);
  const [llmModels, setLlmModels] = useState<string[]>([]);
  const [revealedKeys, setRevealedKeys] = useState<Set<string>>(new Set());
  const [customPlugins, setCustomPlugins] = useState<TireCustomPlugin[]>([]);
  const [customPluginLoading, setCustomPluginLoading] = useState(false);
  const [customPluginModalOpen, setCustomPluginModalOpen] = useState(false);
  const [editingPlugin, setEditingPlugin] = useState<TireCustomPlugin | null>(null);
  const [llmProviders, setLlmProviders] = useState<LlmProvider[]>([]);
  const [llmProviderLoading, setLlmProviderLoading] = useState(false);
  const [llmProviderModalOpen, setLlmProviderModalOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<LlmProvider | null>(null);
  const [validatingProviderId, setValidatingProviderId] = useState<number | null>(null);
  const [assignments, setAssignments] = useState<ConfigAssignment[]>([]);
  const [assignmentLoading, setAssignmentLoading] = useState(false);
  const [assignmentModalOpen, setAssignmentModalOpen] = useState(false);
  const [editingAssignment, setEditingAssignment] = useState<ConfigAssignment | null>(null);
  const [userConfig, setUserConfig] = useState<UserConfig | null>(null);
  const [resolvedConfig, setResolvedConfig] = useState<ResolvedConfig | null>(null);
  const [userConfigLoading, setUserConfigLoading] = useState(false);
  const [userConfigSaving, setUserConfigSaving] = useState(false);

  const customerId = localStorage.getItem('customer_id') || 'demo-customer';
  const userRoles: string[] = (() => {
    try {
      const user = localStorage.getItem('user');
      return user ? JSON.parse(user).roles || [] : [];
    } catch { return []; }
  })();
  const isSuperAdmin = userRoles.includes('SUPER_ADMIN');
  const isTenantAdmin = userRoles.includes('TENANT_ADMIN');
  const isAdmin = isSuperAdmin || isTenantAdmin;
  const isCustomerUser = userRoles.includes('CUSTOMER_USER');
  const canAccessLlmTab = isSuperAdmin || isTenantAdmin || isCustomerUser;

  // ──────── 加载SMTP配置 ────────
  const loadSmtpConfig = async () => {
    try {
      setSmtpLoading(true);
      const resp = await apiClient.get<SmtpConfig>('/api/notification-config/smtp/default');
      setSmtpConfig(resp.data);
      smtpForm.setFieldsValue(resp.data);
    } catch {
      // 没有SMTP配置是正常的
      console.log('No SMTP config found');
    } finally {
      setSmtpLoading(false);
    }
  };

  // ──────── 加载通知配置 ────────
  const loadNotifConfig = async () => {
    try {
      setNotifLoading(true);
      const resp = await apiClient.get<NotificationConfig>(
        `/api/v1/customers/${customerId}/notification-config`
      );
      setNotifConfig(resp.data);
      notifForm.setFieldsValue(resp.data);
    } catch {
      console.log('No notification config found');
    } finally {
      setNotifLoading(false);
    }
  };

  const loadTireConfigs = useCallback(async () => {
    if (!isSuperAdmin) return;
    try {
      setTireLoading(true);
      const [apiKeys, general] = await Promise.all([
        getConfigsByCategory('tire_api_keys'),
        getConfigsByCategory('tire_general'),
      ]);
      setTireConfigs(apiKeys);
      setTireGeneralConfigs(general);
      const formValues: Record<string, string> = {};
      [...apiKeys, ...general].forEach((c) => {
        formValues[c.key] = c.isSecret ? '' : c.value;
      });
      tireForm.setFieldsValue(formValues);
    } catch {
      console.log('Failed to load TIRE configs');
    } finally {
      setTireLoading(false);
    }
  }, [isSuperAdmin, tireForm]);

  const loadLlmConfigs = useCallback(async () => {
    if (!isSuperAdmin) return;
    try {
      setLlmLoading(true);
      const configs = await getConfigsByCategory('llm');
      setLlmConfigs(configs);
      const formValues: Record<string, string> = {};
      configs.forEach((c) => {
        formValues[c.key] = c.isSecret ? '' : c.value;
      });
      llmForm.setFieldsValue(formValues);
    } catch {
      console.log('Failed to load LLM configs');
    } finally {
      setLlmLoading(false);
    }
  }, [isSuperAdmin, llmForm]);

  const loadPluginConfigs = useCallback(async () => {
    if (!isSuperAdmin) return;
    try {
      setPluginLoading(true);
      const configs = await getConfigsByCategory('tire_plugins');
      setPluginConfigs(configs);
      const formValues: Record<string, boolean | number> = {};
      configs.forEach((c) => {
        if (c.key.endsWith('_ENABLED')) {
          formValues[c.key] = c.value === 'true';
          return;
        }
        const num = Number(c.value);
        formValues[c.key] = Number.isNaN(num) ? 0 : num;
      });
      pluginForm.setFieldsValue(formValues);
    } catch {
      console.log('Failed to load plugin configs');
    } finally {
      setPluginLoading(false);
    }
  }, [isSuperAdmin, pluginForm]);

  const loadCustomPlugins = useCallback(async () => {
    if (!isAdmin) return;
    try {
      setCustomPluginLoading(true);
      const data = await listTirePlugins();
      setCustomPlugins(data);
    } catch {
      message.error('加载自定义插件失败');
    } finally {
      setCustomPluginLoading(false);
    }
  }, [isAdmin]);

  const loadLlmProviders = useCallback(async () => {
    if (!canAccessLlmTab && !isAdmin) return;
    try {
      setLlmProviderLoading(true);
      const data = await listLlmProviders();
      setLlmProviders(data);
    } catch {
      message.error('加载LLM服务商失败');
    } finally {
      setLlmProviderLoading(false);
    }
  }, [canAccessLlmTab, isAdmin]);

  const loadAssignments = useCallback(async () => {
    if (!isAdmin) return;
    try {
      setAssignmentLoading(true);
      const data = await listConfigAssignments();
      setAssignments(data);
    } catch {
      message.error('加载配置分配失败');
    } finally {
      setAssignmentLoading(false);
    }
  }, [isAdmin]);

  const loadUserConfigs = useCallback(async () => {
    if (!isCustomerUser) return;
    try {
      setUserConfigLoading(true);
      const [ownConfig, resolved] = await Promise.all([
        getUserConfig(),
        getResolvedConfig(),
      ]);
      setUserConfig(ownConfig);
      setResolvedConfig(resolved);

      const formValues: Record<string, boolean | number | string | undefined> = {
        llmProviderId: ownConfig.llmProviderId ?? undefined,
        useOwnLlm: ownConfig.useOwnLlm ?? false,
        useOwnTire: ownConfig.useOwnTire ?? false,
      };
      TI_API_KEY_NAMES.forEach((key) => {
        formValues[key] = ownConfig.tireApiKeys?.[key] || '';
      });
      userConfigForm.setFieldsValue(formValues);
    } catch {
      message.error('加载我的配置失败');
    } finally {
      setUserConfigLoading(false);
    }
  }, [isCustomerUser, userConfigForm]);

  useEffect(() => {
    loadSmtpConfig();
    loadNotifConfig();
    loadTireConfigs();
    loadPluginConfigs();
    loadLlmConfigs();
    loadCustomPlugins();
    loadLlmProviders();
    loadAssignments();
    loadUserConfigs();
  }, []);

  // ──────── 保存基础设置 ────────
  const handleBasicSave = (values: {
    customer_id: string;
    auto_refresh: boolean;
    refresh_interval: number;
    region: RegionId;
  }) => {
    localStorage.setItem('customer_id', values.customer_id);
    localStorage.setItem('auto_refresh', String(values.auto_refresh));
    localStorage.setItem('refresh_interval', String(values.refresh_interval));
    switchRegion(values.region);
    message.success('基础设置已保存');
  };

  // ──────── 保存SMTP ────────
  const handleSmtpSave = async (values: Record<string, unknown>) => {
    try {
      if (smtpConfig?.id) {
        await apiClient.put(`/api/notification-config/smtp/${smtpConfig.id}`, values);
      } else {
        await apiClient.post('/api/notification-config/smtp', values);
      }
      message.success('SMTP配置已保存');
      loadSmtpConfig();
    } catch {
      message.error('SMTP配置保存失败');
    }
  };

  // ──────── 保存通知偏好 ────────
  const handleNotifSave = async (values: Record<string, unknown>) => {
    try {
      await apiClient.put(
        `/api/v1/customers/${customerId}/notification-config`,
        values
      );
      message.success('通知偏好已保存');
      loadNotifConfig();
    } catch {
      message.error('通知偏好保存失败');
    }
  };

  const handleTireSave = async (values: Record<string, string>) => {
    try {
      setTireSaving(true);
      const updates: Record<string, string> = {};
      const allTireConfigs = [...tireConfigs, ...tireGeneralConfigs];
      Object.entries(values).forEach(([key, val]) => {
        const configDef = allTireConfigs.find((c) => c.key === key);
        if (configDef?.isSecret && (!val || val === '')) return;
        if (val !== undefined && val !== null) updates[key] = val;
      });
      if (Object.keys(updates).length > 0) {
        await batchUpdateConfigs(updates);
      }
      message.success('TIRE配置已保存，请运行 apply-tire-config.sh 同步到集群');
      setRevealedKeys(new Set());
      loadTireConfigs();
    } catch {
      message.error('TIRE配置保存失败');
    } finally {
      setTireSaving(false);
    }
  };

  const handleLlmSave = async (values: Record<string, string>) => {
    try {
      setLlmSaving(true);
      const updates: Record<string, string> = {};
      Object.entries(values).forEach(([key, val]) => {
        const configDef = llmConfigs.find((c) => c.key === key);
        if (configDef?.isSecret && (!val || val === '')) return;
        if (val !== undefined && val !== null) updates[key] = val;
      });
      if (Object.keys(updates).length > 0) {
        await batchUpdateConfigs(updates);
      }
      message.success('LLM配置已保存，请运行 apply-tire-config.sh 同步到集群');
      setRevealedKeys(new Set());
      loadLlmConfigs();
    } catch {
      message.error('LLM配置保存失败');
    } finally {
      setLlmSaving(false);
    }
  };

  const handlePluginSave = async (values: Record<string, boolean | number>) => {
    try {
      setPluginSaving(true);
      const updates: Record<string, string> = {};
      Object.entries(values).forEach(([key, val]) => {
        if (val === undefined || val === null) return;
        const configDef = pluginConfigs.find((c) => c.key === key);
        if (!configDef) return;
        const nextValue = key.endsWith('_ENABLED')
          ? String(Boolean(val))
          : String(val);
        if (nextValue !== configDef.value) {
          updates[key] = nextValue;
        }
      });
      if (Object.keys(updates).length > 0) {
        await batchUpdateConfigs(updates);
      }
      message.success('插件配置已保存，请运行 apply-tire-config.sh 同步到集群');
      loadPluginConfigs();
    } catch {
      message.error('插件配置保存失败');
    } finally {
      setPluginSaving(false);
    }
  };

  const handleLlmValidate = async () => {
    const values = llmForm.getFieldsValue(['LLM_API_KEY', 'LLM_BASE_URL']) as {
      LLM_API_KEY?: string;
      LLM_BASE_URL?: string;
    };
    const apiKeyConfig = llmConfigs.find((c) => c.key === 'LLM_API_KEY');
    if (!values.LLM_API_KEY && apiKeyConfig?.hasValue) {
      message.info('当前API Key为密文存储，请重新输入API Key后再测试连接');
      return;
    }
    if (!values.LLM_API_KEY) {
      message.warning('请先输入LLM API Key');
      return;
    }
    if (!values.LLM_BASE_URL) {
      message.warning('请先输入LLM Base URL');
      return;
    }
    try {
      setLlmValidating(true);
      const result: ConfigLlmValidateResult = await validateLlmConnection(values.LLM_API_KEY, values.LLM_BASE_URL);
      if (result.ok) {
        setLlmModels(result.models || []);
        message.success(`连接测试成功，获取到 ${result.models.length} 个模型`);
      } else {
        message.error(result.error || '连接测试失败');
      }
    } catch {
      message.error('连接测试失败');
    } finally {
      setLlmValidating(false);
    }
  };

  const toggleReveal = (key: string) => {
    setRevealedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const openCustomPluginModal = (plugin?: TireCustomPlugin) => {
    setEditingPlugin(plugin || null);
    if (plugin) {
      customPluginForm.setFieldsValue({
        name: plugin.name,
        slug: plugin.slug,
        description: plugin.description,
        pluginUrl: plugin.pluginUrl,
        apiKey: '',
        authType: plugin.authType || 'bearer',
        authHeader: plugin.authHeader || 'Authorization',
        parserType: plugin.parserType || 'json',
        requestMethod: plugin.requestMethod || 'GET',
        requestBody: plugin.requestBody,
        responsePath: plugin.responsePath,
        enabled: plugin.enabled,
        priority: plugin.priority,
        timeout: plugin.timeout,
      });
    } else {
      customPluginForm.setFieldsValue({
        authType: 'bearer',
        authHeader: 'Authorization',
        parserType: 'json',
        requestMethod: 'GET',
        enabled: true,
        priority: 50,
        timeout: 30,
      });
    }
    setCustomPluginModalOpen(true);
  };

  const handleCustomPluginSubmit = async () => {
    try {
      const values = await customPluginForm.validateFields();
      if (editingPlugin) {
        await updateTirePlugin(editingPlugin.id, values as Partial<TireCustomPlugin>);
        message.success('自定义插件已更新');
      } else {
        await createTirePlugin(values as Partial<TireCustomPlugin>);
        message.success('自定义插件已创建');
      }
      setCustomPluginModalOpen(false);
      customPluginForm.resetFields();
      setEditingPlugin(null);
      loadCustomPlugins();
    } catch {
    }
  };

  const handleDeleteCustomPlugin = (plugin: TireCustomPlugin) => {
    Modal.confirm({
      title: `确定删除插件「${plugin.name}」吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        await deleteTirePlugin(plugin.id);
        message.success('自定义插件已删除');
        loadCustomPlugins();
      },
    });
  };

  const openLlmProviderModal = (provider?: LlmProvider) => {
    setEditingProvider(provider || null);
    if (provider) {
      llmProviderForm.setFieldsValue({
        name: provider.name,
        apiKey: '',
        model: provider.model,
        baseUrl: provider.baseUrl,
        isDefault: provider.isDefault,
        enabled: provider.enabled,
      });
    } else {
      llmProviderForm.setFieldsValue({
        enabled: true,
        isDefault: false,
      });
    }
    setLlmProviderModalOpen(true);
  };

  const handleLlmProviderSubmit = async () => {
    try {
      const values = await llmProviderForm.validateFields();
      if (editingProvider) {
        await updateLlmProvider(editingProvider.id, values as Partial<LlmProvider>);
        message.success('LLM服务商已更新');
      } else {
        await createLlmProvider(values as Partial<LlmProvider>);
        message.success('LLM服务商已创建');
      }
      setLlmProviderModalOpen(false);
      llmProviderForm.resetFields();
      setEditingProvider(null);
      loadLlmProviders();
      if (isAdmin) {
        loadAssignments();
      }
    } catch {
    }
  };

  const handleDeleteLlmProvider = (provider: LlmProvider) => {
    Modal.confirm({
      title: `确定删除LLM服务商「${provider.name}」吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        await deleteLlmProvider(provider.id);
        message.success('LLM服务商已删除');
        loadLlmProviders();
        if (isAdmin) {
          loadAssignments();
        }
      },
    });
  };

  const handleValidateProvider = async (providerId: number) => {
    try {
      setValidatingProviderId(providerId);
      const result: LlmValidateResult = await validateLlmProvider(providerId);
      if (result.ok) {
        message.success(`连接测试成功，获取到 ${result.models.length} 个模型`);
      } else {
        message.error(result.error || '连接测试失败');
      }
    } catch {
      message.error('连接测试失败');
    } finally {
      setValidatingProviderId(null);
    }
  };

  const openAssignmentModal = async (assignment?: ConfigAssignment) => {
    setEditingAssignment(assignment || null);
    if (!assignment) {
      assignmentForm.setFieldsValue({
        customerId: undefined,
        llmProviderId: undefined,
        lockLlm: false,
        lockTire: false,
      });
      TI_API_KEY_NAMES.forEach((key) => assignmentForm.setFieldValue(key, ''));
      setAssignmentModalOpen(true);
      return;
    }

    try {
      const detail = await getConfigAssignment(assignment.customerId);
      assignmentForm.setFieldsValue({
        customerId: detail.customerId,
        llmProviderId: detail.llmProviderId ?? undefined,
        lockLlm: detail.lockLlm ?? false,
        lockTire: detail.lockTire ?? false,
      });
      TI_API_KEY_NAMES.forEach((key) => {
        assignmentForm.setFieldValue(key, detail.tireApiKeys?.[key] || '');
      });
    } catch {
      message.error('加载配置分配详情失败');
      return;
    }

    setAssignmentModalOpen(true);
  };

  const handleAssignmentSubmit = async () => {
    try {
      const values = await assignmentForm.validateFields();
      const tireApiKeys: Record<string, string> = {};
      TI_API_KEY_NAMES.forEach((key) => {
        if (values[key]) {
          tireApiKeys[key] = values[key];
        }
      });

      await assignConfig(values.customerId, {
        llmProviderId: values.llmProviderId || null,
        tireApiKeys,
        lockLlm: values.lockLlm || false,
        lockTire: values.lockTire || false,
      });
      message.success(editingAssignment ? '配置分配已更新' : '配置分配已创建');
      setAssignmentModalOpen(false);
      assignmentForm.resetFields();
      setEditingAssignment(null);
      loadAssignments();
    } catch {
    }
  };

  const handleRemoveAssignment = (assignment: ConfigAssignment) => {
    Modal.confirm({
      title: `确定移除客户「${assignment.customerId}」的分配吗？`,
      okText: '确定',
      cancelText: '取消',
      okType: 'danger',
      onOk: async () => {
        await unassignConfig(assignment.customerId);
        message.success('配置分配已移除');
        loadAssignments();
      },
    });
  };

  const handleUserConfigSubmit = async () => {
    try {
      const values = await userConfigForm.validateFields();
      const tireApiKeys: Record<string, string> = {};
      TI_API_KEY_NAMES.forEach((key) => {
        if (values[key]) {
          tireApiKeys[key] = values[key];
        }
      });

      setUserConfigSaving(true);
      await saveUserConfig({
        llmProviderId: values.llmProviderId || null,
        tireApiKeys,
        useOwnLlm: values.useOwnLlm || false,
        useOwnTire: values.useOwnTire || false,
      });
      message.success('我的配置已保存');
      await loadUserConfigs();
    } catch {
      message.error('保存我的配置失败');
    } finally {
      setUserConfigSaving(false);
    }
  };

  const resolveSourceColor = (source?: string) => {
    if (source === 'admin_locked') return 'red';
    if (source === 'user_own') return 'blue';
    if (source === 'admin_assigned') return 'green';
    return 'default';
  };

  const renderConfigField = (config: SystemConfig) => {
    const isRevealed = revealedKeys.has(config.key);
    return (
      <Form.Item
        key={config.key}
        label={
          <Space>
            {config.description || config.key}
            {config.isSecret && config.hasValue && (
              <Tag color="green">已配置</Tag>
            )}
            {config.isSecret && !config.hasValue && (
              <Tag color="orange">未配置</Tag>
            )}
          </Space>
        }
        name={config.key}
        tooltip={config.key}
      >
        {config.isSecret ? (
          <Input
            placeholder={config.hasValue ? '留空则保留原值' : '请输入API Key'}
            type={isRevealed ? 'text' : 'password'}
            suffix={
              <Button
                type="text"
                size="small"
                icon={isRevealed ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                onClick={() => toggleReveal(config.key)}
              />
            }
          />
        ) : (
          <Input placeholder={`请输入 ${config.description || config.key}`} />
        )}
      </Form.Item>
    );
  };

  const pluginLabelMap: Record<string, string> = {
    abuseipdb: 'AbuseIPDB',
    virustotal: 'VirusTotal',
    otx: 'AlienVault OTX',
    greynoise: 'GreyNoise',
    shodan: 'Shodan',
    rdap: 'RDAP',
    reverse_dns: 'Reverse DNS',
    honeynet: 'Honeynet',
    internal_flow: 'Internal Flow',
    threatbook: 'Threatbook (微步在线)',
    tianjiyoumeng: 'TianjiYoumeng (天际友盟)',
  };

  const pluginOrder = [
    'abuseipdb',
    'virustotal',
    'otx',
    'greynoise',
    'shodan',
    'rdap',
    'reverse_dns',
    'honeynet',
    'internal_flow',
    'threatbook',
    'tianjiyoumeng',
  ];

  const pluginGroupedConfigs = pluginConfigs.reduce<Record<string, Partial<Record<'enabled' | 'priority' | 'timeout', string>>>>((acc, config) => {
    const match = config.key.match(/^PLUGIN_(.+)_(ENABLED|PRIORITY|TIMEOUT)$/);
    if (!match) return acc;
    const pluginName = match[1].toLowerCase();
    const field = match[2].toLowerCase() as 'enabled' | 'priority' | 'timeout';
    acc[pluginName] = acc[pluginName] || {};
    acc[pluginName][field] = config.key;
    return acc;
  }, {});

  const pluginRows = pluginOrder
    .map((pluginName) => {
      const grouped = pluginGroupedConfigs[pluginName];
      if (!grouped?.enabled || !grouped?.priority || !grouped?.timeout) return null;
      return {
        pluginName,
        label: pluginLabelMap[pluginName] || pluginName,
        enabledKey: grouped.enabled,
        priorityKey: grouped.priority,
        timeoutKey: grouped.timeout,
      };
    })
    .filter((item): item is {
      pluginName: string;
      label: string;
      enabledKey: string;
      priorityKey: string;
      timeoutKey: string;
    } => item !== null);

  const customPluginColumns: ColumnsType<TireCustomPlugin> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Slug',
      dataIndex: 'slug',
      key: 'slug',
    },
    {
      title: '插件URL',
      dataIndex: 'pluginUrl',
      key: 'pluginUrl',
      ellipsis: true,
    },
    {
      title: '请求方式',
      dataIndex: 'requestMethod',
      key: 'requestMethod',
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (enabled ? <Tag color="green">开</Tag> : <Tag color="red">关</Tag>),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
    },
    {
      title: '超时(秒)',
      dataIndex: 'timeout',
      key: 'timeout',
    },
    {
      title: '归属',
      dataIndex: 'ownerType',
      key: 'ownerType',
      render: (ownerType: TireCustomPlugin['ownerType']) => {
        if (ownerType === 'SYSTEM') return <Tag color="blue">SYSTEM</Tag>;
        if (ownerType === 'TENANT') return <Tag color="green">TENANT</Tag>;
        return <Tag color="orange">USER</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: TireCustomPlugin) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => openCustomPluginModal(record)}>
            编辑
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDeleteCustomPlugin(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const llmProviderColumns: ColumnsType<LlmProvider> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '模型',
      dataIndex: 'model',
      key: 'model',
    },
    {
      title: 'Base URL',
      dataIndex: 'baseUrl',
      key: 'baseUrl',
      ellipsis: true,
    },
    {
      title: 'API Key',
      dataIndex: 'hasApiKey',
      key: 'hasApiKey',
      render: (hasApiKey: boolean) => (
        hasApiKey ? <Tag color="green">已配置</Tag> : <Tag color="orange">未配置</Tag>
      ),
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      key: 'isDefault',
      render: (isDefault: boolean) => (isDefault ? <Tag color="blue">默认</Tag> : null),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (enabled ? <Tag color="green">开</Tag> : <Tag color="red">关</Tag>),
    },
    {
      title: '归属',
      dataIndex: 'ownerType',
      key: 'ownerType',
      render: (ownerType: LlmProvider['ownerType']) => {
        if (ownerType === 'SYSTEM') return <Tag color="blue">SYSTEM</Tag>;
        if (ownerType === 'TENANT') return <Tag color="green">TENANT</Tag>;
        return <Tag color="orange">USER</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: LlmProvider) => (
        <Space>
          <Button
            icon={<ApiOutlined />}
            loading={validatingProviderId === record.id}
            onClick={() => handleValidateProvider(record.id)}
          >
            连接测试
          </Button>
          <Button icon={<EditOutlined />} onClick={() => openLlmProviderModal(record)}>
            编辑
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDeleteLlmProvider(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const assignmentColumns: ColumnsType<ConfigAssignment> = [
    {
      title: '客户ID',
      dataIndex: 'customerId',
      key: 'customerId',
    },
    {
      title: 'LLM服务商',
      dataIndex: 'llmProviderId',
      key: 'llmProviderId',
      render: (llmProviderId?: number | null) => {
        if (llmProviderId === undefined || llmProviderId === null) {
          return <Tag>未配置</Tag>;
        }
        const provider = llmProviders.find((item) => item.id === llmProviderId);
        return provider ? `${provider.name} (${provider.model})` : String(llmProviderId);
      },
    },
    {
      title: 'LLM锁定',
      dataIndex: 'lockLlm',
      key: 'lockLlm',
      render: (lockLlm?: boolean) => (
        lockLlm ? <Tag color="green">已锁定</Tag> : <Tag>未锁定</Tag>
      ),
    },
    {
      title: 'TI锁定',
      dataIndex: 'lockTire',
      key: 'lockTire',
      render: (lockTire?: boolean) => (
        lockTire ? <Tag color="green">已锁定</Tag> : <Tag>未锁定</Tag>
      ),
    },
    {
      title: 'TI Keys',
      dataIndex: 'hasTireApiKeys',
      key: 'hasTireApiKeys',
      render: (hasTireApiKeys?: boolean) => (
        hasTireApiKeys ? <Tag color="green">已配置</Tag> : <Tag color="orange">未配置</Tag>
      ),
    },
    {
      title: '分配者',
      dataIndex: 'assignedBy',
      key: 'assignedBy',
    },
    {
      title: '分配时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt?: string) => createdAt || '-',
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: ConfigAssignment) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => openAssignmentModal(record)}>
            编辑
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleRemoveAssignment(record)}>
            移除
          </Button>
        </Space>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'basic',
      label: (
        <span>
          <SettingOutlined /> 基础设置
        </span>
      ),
      children: (
        <Card bordered={false}>
          <Alert
            message="客户ID用于多租户数据隔离，修改后所有API请求将使用新的客户ID"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={basicForm}
            layout="vertical"
            onFinish={handleBasicSave}
            initialValues={{
              customer_id: customerId,
              auto_refresh: localStorage.getItem('auto_refresh') !== 'false',
              refresh_interval: Number(localStorage.getItem('refresh_interval')) || 30,
              region: (localStorage.getItem('region') || 'auto') as RegionId,
            }}
            style={{ maxWidth: 500 }}
          >
            <Form.Item
              label="客户ID (Customer ID)"
              name="customer_id"
              rules={[{ required: true, message: '请输入客户ID' }]}
              tooltip="多租户隔离标识，用于API请求"
            >
              <Input prefix={<UserOutlined />} placeholder="demo-customer" />
            </Form.Item>

            <Form.Item
              label="部署区域 (Region)"
              name="region"
              tooltip="选择API服务器区域，影响所有请求的目标端点"
            >
              <Select>
                {Object.values(REGION_ENDPOINTS).map((r) => (
                  <Select.Option key={r.id} value={r.id}>
                    <Space>
                      <GlobalOutlined />
                      {r.label}
                      <Tag color={r.id === 'auto' ? 'default' : r.id === 'cn' ? 'red' : 'blue'}>
                        {r.description}
                      </Tag>
                    </Space>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              label="自动刷新"
              name="auto_refresh"
              valuePropName="checked"
              tooltip="启用后仪表盘和监控页面将自动刷新数据"
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item
              label="刷新间隔 (秒)"
              name="refresh_interval"
              rules={[{ required: true, message: '请输入刷新间隔' }]}
              tooltip="自动刷新的时间间隔，建议10-300秒"
            >
              <InputNumber min={10} max={300} step={5} style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                保存基础设置
              </Button>
            </Form.Item>
          </Form>

          <Divider />
          <Descriptions title="当前环境信息" bordered column={1} size="small">
            <Descriptions.Item label="API基础地址">
              {import.meta.env.VITE_API_BASE_URL || '/api'}
            </Descriptions.Item>
            <Descriptions.Item label="当前客户ID">
              <Tag color="blue">{customerId}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="当前区域">
              <Tag color="green">
                {REGION_ENDPOINTS[(localStorage.getItem('region') || 'auto') as RegionId]?.label || 'Auto'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="应用版本">v1.0.0</Descriptions.Item>
          </Descriptions>
        </Card>
      ),
    },
    {
      key: 'smtp',
      label: (
        <span>
          <MailOutlined /> SMTP邮件
        </span>
      ),
      children: (
        <Card bordered={false} loading={smtpLoading}>
          <Alert
            message="SMTP配置用于告警邮件通知，请确保SMTP服务器可达"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={smtpForm}
            layout="vertical"
            onFinish={handleSmtpSave}
            style={{ maxWidth: 600 }}
          >
            <Row gutter={16}>
              <Col span={16}>
                <Form.Item
                  label="SMTP主机"
                  name="host"
                  rules={[{ required: true, message: '请输入SMTP主机' }]}
                >
                  <Input placeholder="smtp.example.com" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  label="端口"
                  name="port"
                  rules={[{ required: true, message: '请输入端口' }]}
                >
                  <InputNumber min={1} max={65535} placeholder="587" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="用户名" name="username">
                  <Input placeholder="user@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="密码" name="password">
                  <Input.Password placeholder="••••••" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="发件人地址"
                  name="fromAddress"
                  rules={[{ required: true, message: '请输入发件人地址' }]}
                >
                  <Input placeholder="noreply@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="发件人名称" name="fromName">
                  <Input placeholder="威胁检测系统" />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label="加密方式" name="encryption" initialValue="TLS">
              <Select
                options={[
                  { label: 'TLS', value: 'TLS' },
                  { label: 'SSL', value: 'SSL' },
                  { label: '无加密', value: 'NONE' },
                ]}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue={true}>
                  <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="设为默认"
                  name="isDefault"
                  valuePropName="checked"
                  initialValue={true}
                >
                  <Switch checkedChildren="是" unCheckedChildren="否" />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  保存SMTP配置
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadSmtpConfig}>
                  刷新
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
    {
      key: 'notification',
      label: (
        <span>
          <BellOutlined /> 通知偏好
        </span>
      ),
      children: (
        <Card bordered={false} loading={notifLoading}>
          <Alert
            message={`当前客户: ${customerId} — 通知配置将应用于该客户的所有告警`}
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={notifForm}
            layout="vertical"
            onFinish={handleNotifSave}
            style={{ maxWidth: 600 }}
          >
            <Form.Item
              label="邮件通知"
              name="emailEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item
              label="邮件接收人"
              name="emailRecipients"
              tooltip="多个邮箱地址用回车分隔"
            >
              <Select
                mode="tags"
                placeholder="输入邮箱地址后按回车"
                tokenSeparators={[',', ';']}
              />
            </Form.Item>

            <Divider />

            <Form.Item
              label="SMS通知"
              name="smsEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Divider />

            <Form.Item
              label="Slack通知"
              name="slackEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item label="Slack Webhook URL" name="slackWebhookUrl">
              <Input placeholder="https://hooks.slack.com/services/..." />
            </Form.Item>

            <Divider />

            <Form.Item
              label="Webhook通知"
              name="webhookEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item label="Webhook URL" name="webhookUrl">
              <Input placeholder="https://your-webhook-endpoint.com/alerts" />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  保存通知偏好
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadNotifConfig}>
                  刷新
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
    ...(isSuperAdmin
      ? [
          {
            key: 'tire',
            label: (
              <span>
                <ApiOutlined /> TIRE配置
              </span>
            ),
            children: (
              <Card bordered={false}>
                <Spin spinning={tireLoading}>
                  <Alert
                    message="威胁情报API密钥配置"
                    description="配置各威胁情报源的API密钥。保存后需运行 apply-tire-config.sh 脚本将配置同步到K8s集群并重启TIRE服务。Secret字段留空则保留原值。"
                    type="info"
                    showIcon
                    style={{ marginBottom: 24 }}
                  />
                  <Form
                    form={tireForm}
                    layout="vertical"
                    onFinish={handleTireSave}
                    style={{ maxWidth: 600 }}
                  >
                    {tireConfigs.length > 0 && (
                      <>
                        <Divider orientation="left">
                          <Text strong>API密钥</Text>
                        </Divider>
                        {tireConfigs.map(renderConfigField)}
                      </>
                    )}
                    {tireGeneralConfigs.length > 0 && (
                      <>
                        <Divider orientation="left">
                          <Text strong>通用设置</Text>
                        </Divider>
                        {tireGeneralConfigs.map(renderConfigField)}
                      </>
                    )}
                    {tireConfigs.length === 0 && tireGeneralConfigs.length === 0 && (
                      <Alert
                        message="未找到TIRE配置项"
                        description="请先执行 30-system-config.sql 初始化配置数据"
                        type="warning"
                      />
                    )}
                    <Form.Item>
                      <Space>
                        <Button
                          type="primary"
                          htmlType="submit"
                          icon={<SaveOutlined />}
                          loading={tireSaving}
                        >
                          保存TIRE配置
                        </Button>
                        <Button icon={<ReloadOutlined />} onClick={loadTireConfigs}>
                          刷新
                        </Button>
                      </Space>
                    </Form.Item>
                  </Form>
                </Spin>
              </Card>
            ),
          },
        ]
      : []),
    ...(isAdmin
      ? [
          {
            key: 'plugins',
            label: (
              <span>
                <AppstoreOutlined /> 插件管理
              </span>
            ),
            children: (
              <Card bordered={false}>
                {isSuperAdmin && (
                  <Spin spinning={pluginLoading}>
                    <Alert
                      message="TIRE插件配置"
                      description="可按需启用/禁用插件，并调整优先级和超时时间。保存后需运行 apply-tire-config.sh 同步到集群。"
                      type="info"
                      showIcon
                      style={{ marginBottom: 24 }}
                    />
                    <Form
                      form={pluginForm}
                      layout="vertical"
                      onFinish={handlePluginSave}
                    >
                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: '2fr 1fr 1fr 1fr',
                          gap: 12,
                          alignItems: 'center',
                          marginBottom: 12,
                          padding: '0 8px',
                        }}
                      >
                        <Text type="secondary">插件名称</Text>
                        <Text type="secondary">启用状态</Text>
                        <Text type="secondary">优先级</Text>
                        <Text type="secondary">超时（秒）</Text>
                      </div>
                      <Space direction="vertical" style={{ width: '100%' }} size={8}>
                        {pluginRows.map((row) => (
                          <Card key={row.pluginName} size="small">
                            <div
                              style={{
                                display: 'grid',
                                gridTemplateColumns: '2fr 1fr 1fr 1fr',
                                gap: 12,
                                alignItems: 'center',
                              }}
                            >
                              <Text strong>{row.label}</Text>
                              <Form.Item name={row.enabledKey} valuePropName="checked" style={{ marginBottom: 0 }}>
                                <Switch checkedChildren="开" unCheckedChildren="关" />
                              </Form.Item>
                              <Form.Item name={row.priorityKey} style={{ marginBottom: 0 }}>
                                <InputNumber min={1} max={100} style={{ width: '100%' }} />
                              </Form.Item>
                              <Form.Item name={row.timeoutKey} style={{ marginBottom: 0 }}>
                                <InputNumber min={5} max={300} addonAfter="秒" style={{ width: '100%' }} />
                              </Form.Item>
                            </div>
                          </Card>
                        ))}
                      </Space>
                      {pluginRows.length === 0 && (
                        <Alert
                          message="未找到插件配置项"
                          description="请先执行 30-system-config.sql 初始化配置数据"
                          type="warning"
                          style={{ marginTop: 12 }}
                        />
                      )}
                      <Form.Item style={{ marginTop: 24 }}>
                        <Space>
                          <Button
                            type="primary"
                            htmlType="submit"
                            icon={<SaveOutlined />}
                            loading={pluginSaving}
                          >
                            保存插件配置
                          </Button>
                          <Button icon={<ReloadOutlined />} onClick={loadPluginConfigs}>
                            刷新
                          </Button>
                        </Space>
                      </Form.Item>
                    </Form>
                  </Spin>
                )}

                <Divider orientation="left">自定义插件</Divider>
                <Space style={{ marginBottom: 16 }}>
                  {isAdmin && (
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openCustomPluginModal()}>
                      添加自定义插件
                    </Button>
                  )}
                  <Button icon={<ReloadOutlined />} onClick={loadCustomPlugins}>
                    刷新
                  </Button>
                </Space>
                <Table<TireCustomPlugin>
                  rowKey="id"
                  loading={customPluginLoading}
                  columns={customPluginColumns}
                  dataSource={customPlugins}
                  pagination={{ pageSize: 10 }}
                />
              </Card>
            ),
          },
        ]
      : []),
    ...(canAccessLlmTab
      ? [
          {
            key: 'llm',
            label: (
              <span>
                <RobotOutlined /> LLM配置
              </span>
            ),
            children: (
              <Card bordered={false}>
                {isSuperAdmin && (
                  <Spin spinning={llmLoading}>
                    <Alert
                      message="大语言模型 (LLM) 配置"
                      description="用于TIRE威胁情报报告生成的LLM服务配置。支持OpenAI兼容API。保存后需运行 apply-tire-config.sh 同步到集群。"
                      type="info"
                      showIcon
                      style={{ marginBottom: 24 }}
                    />
                    <Divider orientation="left">全局LLM配置 (system_config)</Divider>
                    <Form
                      form={llmForm}
                      layout="vertical"
                      onFinish={handleLlmSave}
                      style={{ maxWidth: 600 }}
                    >
                      {llmConfigs.map((config) => {
                        if (config.key !== 'LLM_MODEL') {
                          return renderConfigField(config);
                        }
                        if (llmModels.length === 0) {
                          return renderConfigField(config);
                        }
                        return (
                          <Form.Item
                            key={config.key}
                            label={
                              <Space>
                                {config.description || config.key}
                              </Space>
                            }
                            name={config.key}
                            tooltip={config.key}
                            getValueProps={(value: string) => ({ value: value ? [value] : [] })}
                            normalize={(value: string[]) => {
                              const candidates = value.filter((item) => item !== '__other__');
                              return candidates[candidates.length - 1] || '';
                            }}
                          >
                            <Select
                              mode="tags"
                              placeholder="请选择或输入模型名称"
                              options={[
                                ...llmModels.map((model) => ({ label: model, value: model })),
                                { label: 'Other（可直接输入）', value: '__other__', disabled: true },
                              ]}
                              tokenSeparators={[',']}
                            />
                          </Form.Item>
                        );
                      })}
                      {llmConfigs.length === 0 && (
                        <Alert
                          message="未找到LLM配置项"
                          description="请先执行 30-system-config.sql 初始化配置数据"
                          type="warning"
                        />
                      )}
                      <Form.Item>
                        <Space>
                          <Button
                            type="primary"
                            htmlType="submit"
                            icon={<SaveOutlined />}
                            loading={llmSaving}
                          >
                            保存LLM配置
                          </Button>
                          <Button
                            icon={<ApiOutlined />}
                            onClick={handleLlmValidate}
                            loading={llmValidating}
                          >
                            测试连接
                          </Button>
                          <Button icon={<ReloadOutlined />} onClick={loadLlmConfigs}>
                            刷新
                          </Button>
                        </Space>
                      </Form.Item>
                    </Form>
                  </Spin>
                )}

                <Divider orientation="left">LLM服务商管理</Divider>
                <Space style={{ marginBottom: 16 }}>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => openLlmProviderModal()}>
                    添加LLM服务商
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadLlmProviders}>
                    刷新
                  </Button>
                </Space>
                <Table<LlmProvider>
                  rowKey="id"
                  loading={llmProviderLoading}
                  columns={llmProviderColumns}
                  dataSource={llmProviders}
                  pagination={{ pageSize: 10 }}
                />
              </Card>
            ),
          },
        ]
      : []),
    ...(isCustomerUser
      ? [
          {
            key: 'my-config',
            label: (
              <span>
                <UserOutlined /> 我的配置
              </span>
            ),
            children: (
              <Card bordered={false}>
                <Spin spinning={userConfigLoading}>
                  <Divider orientation="left">当前生效配置</Divider>
                  <Card size="small" style={{ marginBottom: 16 }}>
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="LLM来源">
                        <Tag color={resolveSourceColor(resolvedConfig?.llmSource)}>
                          {resolvedConfig?.llmSource || 'system_default'}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="TI来源">
                        <Tag color={resolveSourceColor(resolvedConfig?.tireSource)}>
                          {resolvedConfig?.tireSource || 'system_default'}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="生效LLM服务商">
                        {resolvedConfig?.providerName
                          ? `${resolvedConfig.providerName}${resolvedConfig.providerModel ? ` (${resolvedConfig.providerModel})` : ''}`
                          : '未配置'}
                      </Descriptions.Item>
                      <Descriptions.Item label="生效TI API Keys">
                        <Space wrap>
                          {TI_API_KEY_NAMES.map((key) => (
                            resolvedConfig?.tireApiKeys?.[key]
                              ? (
                                <Tag key={key} color="green">
                                  {key}: {resolvedConfig.tireApiKeys[key]}
                                </Tag>
                                )
                              : (
                                <Tag key={key}>
                                  {key}: 未配置
                                </Tag>
                                )
                          ))}
                        </Space>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>

                  <Divider orientation="left">自定义配置</Divider>
                  <Form form={userConfigForm} layout="vertical">
                    {userConfig?.lockLlm ? (
                      <Alert
                        type="warning"
                        showIcon
                        message="管理员已锁定LLM配置，无法修改"
                        style={{ marginBottom: 16 }}
                      />
                    ) : (
                      <>
                        <Form.Item label="使用自己的LLM配置" name="useOwnLlm" valuePropName="checked">
                          <Switch />
                        </Form.Item>
                        <Form.Item label="LLM服务商" name="llmProviderId">
                          <Select
                            allowClear
                            options={llmProviders.map((provider) => ({
                              label: `${provider.name} (${provider.model})`,
                              value: provider.id,
                            }))}
                          />
                        </Form.Item>
                      </>
                    )}

                    <Divider />

                    {userConfig?.lockTire ? (
                      <Alert
                        type="warning"
                        showIcon
                        message="管理员已锁定TI配置，无法修改"
                        style={{ marginBottom: 16 }}
                      />
                    ) : (
                      <>
                        <Form.Item label="使用自己的TI配置" name="useOwnTire" valuePropName="checked">
                          <Switch />
                        </Form.Item>
                        {TI_API_KEY_NAMES.map((key) => (
                          <Form.Item key={key} label={key} name={key}>
                            <Input.Password placeholder="留空则保留原值" />
                          </Form.Item>
                        ))}
                      </>
                    )}

                    <Form.Item>
                      <Space>
                        <Button
                          type="primary"
                          icon={<SaveOutlined />}
                          onClick={handleUserConfigSubmit}
                          loading={userConfigSaving}
                          disabled={userConfig?.lockLlm && userConfig?.lockTire}
                        >
                          保存我的配置
                        </Button>
                        <Button icon={<ReloadOutlined />} onClick={loadUserConfigs}>
                          刷新
                        </Button>
                      </Space>
                    </Form.Item>
                  </Form>
                </Spin>
              </Card>
            ),
          },
        ]
      : []),
    ...(isAdmin
      ? [
          {
            key: 'assignments',
            label: (
              <span>
                <LinkOutlined /> 配置分配
              </span>
            ),
            children: (
              <Card bordered={false}>
                <Space style={{ marginBottom: 16 }}>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => openAssignmentModal()}>
                    新建配置分配
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadAssignments}>
                    刷新分配
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadLlmProviders}>
                    刷新服务商
                  </Button>
                </Space>
                <Table<ConfigAssignment>
                  rowKey={(record) => `${record.customerId}-${record.llmProviderId}`}
                  loading={assignmentLoading}
                  columns={assignmentColumns}
                  dataSource={assignments}
                  pagination={{ pageSize: 10 }}
                />
              </Card>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <Card title="系统设置" bordered={false}>
        <Tabs defaultActiveKey="basic" items={tabItems} />
      </Card>

      <Modal
        title={editingPlugin ? '编辑自定义插件' : '添加自定义插件'}
        open={customPluginModalOpen}
        onCancel={() => {
          setCustomPluginModalOpen(false);
          customPluginForm.resetFields();
          setEditingPlugin(null);
        }}
        onOk={handleCustomPluginSubmit}
        okText="保存"
        cancelText="取消"
      >
        <Form form={customPluginForm} layout="vertical">
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Slug" name="slug" rules={[{ required: true, message: '请输入Slug' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="插件URL" name="pluginUrl" rules={[{ required: true, message: '请输入插件URL' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password placeholder="留空则保留原值" />
          </Form.Item>
          <Form.Item label="认证类型" name="authType" initialValue="bearer">
            <Select
              options={[
                { label: 'bearer', value: 'bearer' },
                { label: 'header', value: 'header' },
                { label: 'query', value: 'query' },
              ]}
            />
          </Form.Item>
          <Form.Item label="认证头" name="authHeader" initialValue="Authorization">
            <Input />
          </Form.Item>
          <Form.Item label="解析类型" name="parserType" initialValue="json">
            <Select
              options={[
                { label: 'json', value: 'json' },
                { label: 'xml', value: 'xml' },
                { label: 'regex', value: 'regex' },
              ]}
            />
          </Form.Item>
          <Form.Item label="请求方式" name="requestMethod" initialValue="GET">
            <Select
              options={[
                { label: 'GET', value: 'GET' },
                { label: 'POST', value: 'POST' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) =>
              getFieldValue('requestMethod') === 'POST' ? (
                <Form.Item label="请求体" name="requestBody">
                  <Input.TextArea rows={3} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item label="响应路径" name="responsePath">
            <Input />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue>
            <Switch checkedChildren="开" unCheckedChildren="关" />
          </Form.Item>
          <Form.Item label="优先级" name="priority" initialValue={50}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="超时(秒)" name="timeout" initialValue={30}>
            <InputNumber min={5} max={300} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingProvider ? '编辑LLM服务商' : '添加LLM服务商'}
        open={llmProviderModalOpen}
        onCancel={() => {
          setLlmProviderModalOpen(false);
          llmProviderForm.resetFields();
          setEditingProvider(null);
        }}
        onOk={handleLlmProviderSubmit}
        okText="保存"
        cancelText="取消"
      >
        <Form form={llmProviderForm} layout="vertical">
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password placeholder="留空则保留原值" />
          </Form.Item>
          <Form.Item label="模型" name="model" rules={[{ required: true, message: '请输入模型' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Base URL" name="baseUrl" rules={[{ required: true, message: '请输入Base URL' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="默认" name="isDefault" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue>
            <Switch checkedChildren="开" unCheckedChildren="关" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingAssignment ? '编辑配置分配' : '配置分配'}
        open={assignmentModalOpen}
        onCancel={() => {
          setAssignmentModalOpen(false);
          assignmentForm.resetFields();
          setEditingAssignment(null);
        }}
        onOk={handleAssignmentSubmit}
        okText="保存"
        cancelText="取消"
      >
        <Form form={assignmentForm} layout="vertical">
          <Form.Item label="客户ID" name="customerId" rules={[{ required: true, message: '请输入客户ID' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="LLM服务商" name="llmProviderId">
            <Select
              allowClear
              options={llmProviders.map((provider) => ({
                label: `${provider.name} (${provider.model})`,
                value: provider.id,
              }))}
            />
          </Form.Item>

          <Divider orientation="left">威胁情报 API Keys</Divider>
          {TI_API_KEY_NAMES.map((key) => (
            <Form.Item key={key} label={key} name={key}>
              <Input.Password placeholder="留空则表示不修改" />
            </Form.Item>
          ))}

          <Divider orientation="left">锁定策略</Divider>
          <Alert
            type="warning"
            showIcon
            message="锁定后客户将无法使用自己的配置"
            style={{ marginBottom: 16 }}
          />
          <Form.Item label="锁定LLM配置" name="lockLlm" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="锁定TI配置" name="lockTire" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default Settings;
