import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Switch,
  message,
  Alert,
  Space,
  Divider,
  Select,
  Tag,
  Descriptions,
  Spin,
  Table,
  Modal,
} from 'antd';
import {
  SaveOutlined,
  ReloadOutlined,
  ApiOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { getConfigsByCategory, batchUpdateConfigs, validateLlmConnection } from '@/services/config';
import type { LlmValidateResult as ConfigLlmValidateResult } from '@/services/config';
import {
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
import { usePermission } from '@/hooks/usePermission';
import type { ColumnsType } from 'antd/es/table';
import type {
  LlmProvider,
  ConfigAssignment,
  LlmValidateResult,
  UserConfig,
  ResolvedConfig,
} from '@/services/tire';
import type { SystemConfig } from '@/types';

const TI_API_KEY_NAMES = [
  'ABUSEIPDB_API_KEY',
  'GREYNOISE_API_KEY',
  'OTX_API_KEY',
  'SHODAN_API_KEY',
  'THREATBOOK_API_KEY',
  'TIANJIYOUMENG_API_KEY',
  'VT_API_KEY',
] as const;

const AIConfig = () => {
  const { t } = useTranslation();
  const [llmForm] = Form.useForm();
  const [llmProviderForm] = Form.useForm();
  const [assignmentForm] = Form.useForm();
  const [userConfigForm] = Form.useForm();

  const [llmConfigs, setLlmConfigs] = useState<SystemConfig[]>([]);
  const [llmLoading, setLlmLoading] = useState(false);
  const [llmSaving, setLlmSaving] = useState(false);
  const [llmValidating, setLlmValidating] = useState(false);
  const [llmModels, setLlmModels] = useState<string[]>([]);
  const [revealedKeys, setRevealedKeys] = useState<Set<string>>(new Set());
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

  const { isSuperAdmin, isTenantAdmin, isCustomerUser, isAdminRole: isAdmin } = usePermission();
  const canAccessLlmTab = isSuperAdmin || isTenantAdmin || isCustomerUser;

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

  const loadLlmProviders = useCallback(async () => {
    if (!canAccessLlmTab && !isAdmin) return;
    try {
      setLlmProviderLoading(true);
      const data = await listLlmProviders();
      setLlmProviders(data);
    } catch {
      message.error(t('aiConfig.loadProvidersFailed'));
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
      message.error(t('aiConfig.loadAssignmentsFailed'));
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
      message.error(t('aiConfig.loadMyConfigFailed'));
    } finally {
      setUserConfigLoading(false);
    }
  }, [isCustomerUser, userConfigForm]);

  useEffect(() => {
    loadLlmConfigs();
    loadLlmProviders();
    loadAssignments();
    loadUserConfigs();
  }, []);

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
      message.success(t('aiConfig.globalSaved'));
      setRevealedKeys(new Set());
      loadLlmConfigs();
    } catch {
      message.error(t('aiConfig.globalSaveFailed'));
    } finally {
      setLlmSaving(false);
    }
  };

  const handleLlmValidate = async () => {
    const values = llmForm.getFieldsValue(['LLM_API_KEY', 'LLM_BASE_URL']) as {
      LLM_API_KEY?: string;
      LLM_BASE_URL?: string;
    };
    const apiKeyConfig = llmConfigs.find((c) => c.key === 'LLM_API_KEY');
    if (!values.LLM_API_KEY && apiKeyConfig?.hasValue) {
      message.info(t('aiConfig.reenterApiKeyForValidation'));
      return;
    }
    if (!values.LLM_API_KEY) {
      message.warning(t('aiConfig.enterLlmApiKeyFirst'));
      return;
    }
    if (!values.LLM_BASE_URL) {
      message.warning(t('aiConfig.enterLlmBaseUrlFirst'));
      return;
    }
    try {
      setLlmValidating(true);
      const result: ConfigLlmValidateResult = await validateLlmConnection(values.LLM_API_KEY, values.LLM_BASE_URL);
      if (result.ok) {
        setLlmModels(result.models || []);
        message.success(t('aiConfig.connectionTestSuccess', { count: result.models.length }));
      } else {
        message.error(result.error || t('aiConfig.connectionTestFailed'));
      }
    } catch {
      message.error(t('aiConfig.connectionTestFailed'));
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

  const renderConfigField = (config: SystemConfig) => {
    const isRevealed = revealedKeys.has(config.key);
    return (
      <Form.Item
        key={config.key}
        label={
          <Space>
            {config.description || config.key}
            {config.isSecret && config.hasValue && (
              <Tag color="green">{t('common.configured')}</Tag>
            )}
            {config.isSecret && !config.hasValue && (
              <Tag color="orange">{t('common.notConfigured')}</Tag>
            )}
          </Space>
        }
        name={config.key}
        tooltip={config.key}
      >
        {config.isSecret ? (
          <Input
            placeholder={config.hasValue ? t('common.keepEmptyToKeepValue') : t('aiConfig.enterApiKey')}
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
          <Input placeholder={t('aiConfig.enterConfigValue', { key: config.description || config.key })} />
        )}
      </Form.Item>
    );
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
        message.success(t('aiConfig.providerUpdated'));
      } else {
        await createLlmProvider(values as Partial<LlmProvider>);
        message.success(t('aiConfig.providerCreated'));
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
      title: t('aiConfig.confirmDeleteProvider', { name: provider.name }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        await deleteLlmProvider(provider.id);
        message.success(t('aiConfig.providerDeleted'));
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
        message.success(t('aiConfig.connectionTestSuccess', { count: result.models.length }));
      } else {
        message.error(result.error || t('aiConfig.connectionTestFailed'));
      }
    } catch {
      message.error(t('aiConfig.connectionTestFailed'));
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
      message.error(t('aiConfig.loadAssignmentDetailFailed'));
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
      message.success(editingAssignment ? t('aiConfig.assignmentUpdated') : t('aiConfig.assignmentCreated'));
      setAssignmentModalOpen(false);
      assignmentForm.resetFields();
      setEditingAssignment(null);
      loadAssignments();
    } catch {
    }
  };

  const handleRemoveAssignment = (assignment: ConfigAssignment) => {
    Modal.confirm({
      title: t('aiConfig.confirmRemoveAssignment', { customerId: assignment.customerId }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        await unassignConfig(assignment.customerId);
        message.success(t('aiConfig.assignmentRemoved'));
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
      message.success(t('aiConfig.myConfigSaved'));
      await loadUserConfigs();
    } catch {
      message.error(t('aiConfig.myConfigSaveFailed'));
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

  const llmProviderColumns: ColumnsType<LlmProvider> = [
    {
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('common.model'),
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
        hasApiKey ? <Tag color="green">{t('common.configured')}</Tag> : <Tag color="orange">{t('common.notConfigured')}</Tag>
      ),
    },
    {
      title: t('common.default'),
      dataIndex: 'isDefault',
      key: 'isDefault',
      render: (isDefault: boolean) => (isDefault ? <Tag color="blue">{t('common.default')}</Tag> : null),
    },
    {
      title: t('common.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (enabled ? <Tag color="green">{t('common.on')}</Tag> : <Tag color="red">{t('common.off')}</Tag>),
    },
    {
      title: t('common.owner'),
      dataIndex: 'ownerType',
      key: 'ownerType',
      render: (ownerType: LlmProvider['ownerType']) => {
        if (ownerType === 'SYSTEM') return <Tag color="blue">SYSTEM</Tag>;
        if (ownerType === 'TENANT') return <Tag color="green">TENANT</Tag>;
        return <Tag color="orange">USER</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: LlmProvider) => (
        <Space>
          <Button
            icon={<ApiOutlined />}
            loading={validatingProviderId === record.id}
            onClick={() => handleValidateProvider(record.id)}
          >
            {t('aiConfig.connectionTest')}
          </Button>
          <Button icon={<EditOutlined />} onClick={() => openLlmProviderModal(record)}>
            {t('common.edit')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDeleteLlmProvider(record)}>
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  const assignmentColumns: ColumnsType<ConfigAssignment> = [
    {
      title: t('common.customerId'),
      dataIndex: 'customerId',
      key: 'customerId',
    },
    {
      title: t('aiConfig.llmProvider'),
      dataIndex: 'llmProviderId',
      key: 'llmProviderId',
      render: (llmProviderId?: number | null) => {
        if (llmProviderId === undefined || llmProviderId === null) {
          return <Tag>{t('common.notConfigured')}</Tag>;
        }
        const provider = llmProviders.find((item) => item.id === llmProviderId);
        return provider ? `${provider.name} (${provider.model})` : String(llmProviderId);
      },
    },
    {
      title: t('aiConfig.llmLocked'),
      dataIndex: 'lockLlm',
      key: 'lockLlm',
      render: (lockLlm?: boolean) => (
        lockLlm ? <Tag color="green">{t('aiConfig.locked')}</Tag> : <Tag>{t('aiConfig.unlocked')}</Tag>
      ),
    },
    {
      title: t('aiConfig.tiLocked'),
      dataIndex: 'lockTire',
      key: 'lockTire',
      render: (lockTire?: boolean) => (
        lockTire ? <Tag color="green">{t('aiConfig.locked')}</Tag> : <Tag>{t('aiConfig.unlocked')}</Tag>
      ),
    },
    {
      title: 'TI Keys',
      dataIndex: 'hasTireApiKeys',
      key: 'hasTireApiKeys',
      render: (hasTireApiKeys?: boolean) => (
        hasTireApiKeys ? <Tag color="green">{t('common.configured')}</Tag> : <Tag color="orange">{t('common.notConfigured')}</Tag>
      ),
    },
    {
      title: t('aiConfig.assignedBy'),
      dataIndex: 'assignedBy',
      key: 'assignedBy',
    },
    {
      title: t('aiConfig.assignedAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (createdAt?: string) => createdAt || '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: ConfigAssignment) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => openAssignmentModal(record)}>
            {t('common.edit')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleRemoveAssignment(record)}>
            {t('common.remove')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {isSuperAdmin && (
        <Card bordered={false}>
          <Spin spinning={llmLoading}>
            <Alert
              message={t('aiConfig.globalTitle')}
              description={t('aiConfig.globalDescription')}
              type="info"
              showIcon
              style={{ marginBottom: 24 }}
            />
            <Divider orientation="left">{t('aiConfig.globalLlmConfig')}</Divider>
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
                      placeholder={t('aiConfig.selectOrInputModel')}
                      options={[
                        ...llmModels.map((model) => ({ label: model, value: model })),
                        { label: t('aiConfig.otherInputHint'), value: '__other__', disabled: true },
                      ]}
                      tokenSeparators={[',']}
                    />
                  </Form.Item>
                );
              })}
              {llmConfigs.length === 0 && (
                <Alert
                  message={t('aiConfig.noLlmConfigItems')}
                  description={t('aiConfig.initHint')}
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
                    {t('aiConfig.saveLlmConfig')}
                  </Button>
                  <Button
                    icon={<ApiOutlined />}
                    onClick={handleLlmValidate}
                    loading={llmValidating}
                  >
                    {t('aiConfig.connectionTest')}
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadLlmConfigs}>
                    {t('common.refresh')}
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Spin>
        </Card>
      )}

      <Card bordered={false}>
        <Divider orientation="left">{t('aiConfig.providerManagement')}</Divider>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openLlmProviderModal()}>
            {t('aiConfig.addLlmProvider')}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadLlmProviders}>
            {t('common.refresh')}
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

      {isAdmin && (
        <Card bordered={false}>
          <Divider orientation="left">{t('aiConfig.configAssignment')}</Divider>
          <Space style={{ marginBottom: 16 }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openAssignmentModal()}>
              {t('aiConfig.newConfigAssignment')}
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadAssignments}>
              {t('aiConfig.refreshAssignments')}
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadLlmProviders}>
              {t('aiConfig.refreshProviders')}
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
      )}

      {isCustomerUser && (
        <Card bordered={false}>
          <Spin spinning={userConfigLoading}>
            <Divider orientation="left">{t('aiConfig.currentEffectiveConfig')}</Divider>
            <Card size="small" style={{ marginBottom: 16 }}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label={t('aiConfig.llmSource')}>
                  <Tag color={resolveSourceColor(resolvedConfig?.llmSource)}>
                    {resolvedConfig?.llmSource || 'system_default'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('aiConfig.tiSource')}>
                  <Tag color={resolveSourceColor(resolvedConfig?.tireSource)}>
                    {resolvedConfig?.tireSource || 'system_default'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('aiConfig.effectiveLlmProvider')}>
                  {resolvedConfig?.providerName
                    ? `${resolvedConfig.providerName}${resolvedConfig.providerModel ? ` (${resolvedConfig.providerModel})` : ''}`
                    : t('common.notConfigured')}
                </Descriptions.Item>
                <Descriptions.Item label={t('aiConfig.effectiveTiApiKeys')}>
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
                            {key}: {t('common.notConfigured')}
                          </Tag>
                        )
                    ))}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Divider orientation="left">{t('aiConfig.customConfig')}</Divider>
            <Form form={userConfigForm} layout="vertical">
              {userConfig?.lockLlm ? (
                <Alert
                  type="warning"
                  showIcon
                  message={t('aiConfig.llmLockedByAdmin')}
                  style={{ marginBottom: 16 }}
                />
              ) : (
                <>
                  <Form.Item label={t('aiConfig.useOwnLlmConfig')} name="useOwnLlm" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item label={t('aiConfig.llmProvider')} name="llmProviderId">
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
                  message={t('aiConfig.tiLockedByAdmin')}
                  style={{ marginBottom: 16 }}
                />
              ) : (
                <>
                  <Form.Item label={t('aiConfig.useOwnTiConfig')} name="useOwnTire" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  {TI_API_KEY_NAMES.map((key) => (
                    <Form.Item key={key} label={key} name={key}>
                      <Input.Password placeholder={t('common.keepEmptyToKeepValue')} />
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
                    {t('aiConfig.saveMyConfig')}
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadUserConfigs}>
                    {t('common.refresh')}
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Spin>
        </Card>
      )}

      <Modal
        title={editingProvider ? t('aiConfig.editLlmProvider') : t('aiConfig.addLlmProvider')}
        open={llmProviderModalOpen}
        onCancel={() => {
          setLlmProviderModalOpen(false);
          llmProviderForm.resetFields();
          setEditingProvider(null);
        }}
        onOk={handleLlmProviderSubmit}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={llmProviderForm} layout="vertical">
          <Form.Item label={t('common.name')} name="name" rules={[{ required: true, message: t('aiConfig.validationNameRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password placeholder={t('common.keepEmptyToKeepValue')} />
          </Form.Item>
          <Form.Item label={t('common.model')} name="model" rules={[{ required: true, message: t('aiConfig.validationModelRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label="Base URL" name="baseUrl" rules={[{ required: true, message: t('aiConfig.validationBaseUrlRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label={t('common.default')} name="isDefault" valuePropName="checked">
            <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
          </Form.Item>
          <Form.Item label={t('common.enabled')} name="enabled" valuePropName="checked" initialValue>
            <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingAssignment ? t('aiConfig.editAssignment') : t('aiConfig.assignment')}
        open={assignmentModalOpen}
        onCancel={() => {
          setAssignmentModalOpen(false);
          assignmentForm.resetFields();
          setEditingAssignment(null);
        }}
        onOk={handleAssignmentSubmit}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={assignmentForm} layout="vertical">
          <Form.Item label={t('common.customerId')} name="customerId" rules={[{ required: true, message: t('aiConfig.validationCustomerIdRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label={t('aiConfig.llmProvider')} name="llmProviderId">
            <Select
              allowClear
              options={llmProviders.map((provider) => ({
                label: `${provider.name} (${provider.model})`,
                value: provider.id,
              }))}
            />
          </Form.Item>

          <Divider orientation="left">{t('aiConfig.threatIntelApiKeys')}</Divider>
          {TI_API_KEY_NAMES.map((key) => (
            <Form.Item key={key} label={key} name={key}>
              <Input.Password placeholder={t('aiConfig.keepEmptyNoChange')} />
            </Form.Item>
          ))}

          <Divider orientation="left">{t('aiConfig.lockStrategy')}</Divider>
          <Alert
            type="warning"
            showIcon
            message={t('aiConfig.lockStrategyHint')}
            style={{ marginBottom: 16 }}
          />
          <Form.Item label={t('aiConfig.lockLlmConfig')} name="lockLlm" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t('aiConfig.lockTiConfig')} name="lockTire" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default AIConfig;
