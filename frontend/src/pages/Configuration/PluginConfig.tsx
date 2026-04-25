import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Button,
  Switch,
  Select,
  message,
  Alert,
  Space,
  Divider,
  Spin,
  Typography,
  Tag,
  Table,
  Modal,
} from 'antd';
import {
  SaveOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { getConfigsByCategory, batchUpdateConfigs } from '@/services/config';
import {
  listTirePlugins,
  createTirePlugin,
  updateTirePlugin,
  deleteTirePlugin,
} from '@/services/tire';
import type { ColumnsType } from 'antd/es/table';
import type { TireCustomPlugin } from '@/services/tire';
import type { SystemConfig } from '@/types';

const { Text } = Typography;

const PluginConfig = () => {
  const [pluginForm] = Form.useForm();
  const [customPluginForm] = Form.useForm();

  const [pluginConfigs, setPluginConfigs] = useState<SystemConfig[]>([]);
  const [pluginLoading, setPluginLoading] = useState(false);
  const [pluginSaving, setPluginSaving] = useState(false);
  const [customPlugins, setCustomPlugins] = useState<TireCustomPlugin[]>([]);
  const [customPluginLoading, setCustomPluginLoading] = useState(false);
  const [customPluginModalOpen, setCustomPluginModalOpen] = useState(false);
  const [editingPlugin, setEditingPlugin] = useState<TireCustomPlugin | null>(null);

  const userRoles: string[] = (() => {
    try {
      const user = localStorage.getItem('user');
      return user ? JSON.parse(user).roles || [] : [];
    } catch { return []; }
  })();
  const isSuperAdmin = userRoles.includes('SUPER_ADMIN');
  const isTenantAdmin = userRoles.includes('TENANT_ADMIN');
  const isAdmin = isSuperAdmin || isTenantAdmin;

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

  useEffect(() => {
    loadPluginConfigs();
    loadCustomPlugins();
  }, []);

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

  return (
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
    </Card>
  );
};

export default PluginConfig;
