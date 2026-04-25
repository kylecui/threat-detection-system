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
      message.success('LLM配置已保存，请运行 apply-tire-config.sh 同步到集群');
      setRevealedKeys(new Set());
      loadLlmConfigs();
    } catch {
      message.error('LLM配置保存失败');
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

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {isSuperAdmin && (
        <Card bordered={false}>
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
        </Card>
      )}

      <Card bordered={false}>
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

      {isAdmin && (
        <Card bordered={false}>
          <Divider orientation="left">配置分配</Divider>
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
      )}

      {isCustomerUser && (
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
      )}

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
    </Space>
  );
};

export default AIConfig;
