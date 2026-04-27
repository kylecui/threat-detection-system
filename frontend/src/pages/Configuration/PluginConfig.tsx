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
import { useTranslation } from 'react-i18next';
import { getConfigsByCategory, batchUpdateConfigs } from '@/services/config';
import { usePermission } from '@/hooks/usePermission';
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
  const { t } = useTranslation();
  const [pluginForm] = Form.useForm();
  const [customPluginForm] = Form.useForm();

  const [pluginConfigs, setPluginConfigs] = useState<SystemConfig[]>([]);
  const [pluginLoading, setPluginLoading] = useState(false);
  const [pluginSaving, setPluginSaving] = useState(false);
  const [customPlugins, setCustomPlugins] = useState<TireCustomPlugin[]>([]);
  const [customPluginLoading, setCustomPluginLoading] = useState(false);
  const [customPluginModalOpen, setCustomPluginModalOpen] = useState(false);
  const [editingPlugin, setEditingPlugin] = useState<TireCustomPlugin | null>(null);

  const { isSuperAdmin, isAdminRole: isAdmin } = usePermission();

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
      message.error(t('pluginConfig.loadCustomPluginsFailed'));
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
      message.success(t('pluginConfig.saved'));
      loadPluginConfigs();
    } catch {
      message.error(t('pluginConfig.saveFailed'));
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
        message.success(t('pluginConfig.customUpdated'));
      } else {
        await createTirePlugin(values as Partial<TireCustomPlugin>);
        message.success(t('pluginConfig.customCreated'));
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
      title: t('pluginConfig.confirmDeletePlugin', { name: plugin.name }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        await deleteTirePlugin(plugin.id);
        message.success(t('pluginConfig.customDeleted'));
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
    threatbook: t('pluginConfig.pluginThreatbook'),
    tianjiyoumeng: t('pluginConfig.pluginTianjiYoumeng'),
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
      title: t('common.name'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Slug',
      dataIndex: 'slug',
      key: 'slug',
    },
    {
      title: t('pluginConfig.pluginUrl'),
      dataIndex: 'pluginUrl',
      key: 'pluginUrl',
      ellipsis: true,
    },
    {
      title: t('pluginConfig.requestMethod'),
      dataIndex: 'requestMethod',
      key: 'requestMethod',
    },
    {
      title: t('common.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (enabled ? <Tag color="green">{t('common.on')}</Tag> : <Tag color="red">{t('common.off')}</Tag>),
    },
    {
      title: t('common.priority'),
      dataIndex: 'priority',
      key: 'priority',
    },
    {
      title: t('common.timeoutSeconds'),
      dataIndex: 'timeout',
      key: 'timeout',
    },
    {
      title: t('common.owner'),
      dataIndex: 'ownerType',
      key: 'ownerType',
      render: (ownerType: TireCustomPlugin['ownerType']) => {
        if (ownerType === 'SYSTEM') return <Tag color="blue">SYSTEM</Tag>;
        if (ownerType === 'TENANT') return <Tag color="green">TENANT</Tag>;
        return <Tag color="orange">USER</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: TireCustomPlugin) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => openCustomPluginModal(record)}>
            {t('common.edit')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDeleteCustomPlugin(record)}>
            {t('common.delete')}
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
            message={t('pluginConfig.title')}
            description={t('pluginConfig.description')}
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
              <Text type="secondary">{t('pluginConfig.pluginName')}</Text>
              <Text type="secondary">{t('pluginConfig.enabledState')}</Text>
              <Text type="secondary">{t('common.priority')}</Text>
              <Text type="secondary">{t('common.timeoutSeconds')}</Text>
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
                      <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
                    </Form.Item>
                    <Form.Item name={row.priorityKey} style={{ marginBottom: 0 }}>
                      <InputNumber min={1} max={100} style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name={row.timeoutKey} style={{ marginBottom: 0 }}>
                      <InputNumber min={5} max={300} addonAfter={t('common.secondShort')} style={{ width: '100%' }} />
                    </Form.Item>
                  </div>
                </Card>
              ))}
            </Space>
            {pluginRows.length === 0 && (
              <Alert
                message={t('pluginConfig.noConfigItems')}
                description={t('pluginConfig.initHint')}
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
                  {t('pluginConfig.save')}
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadPluginConfigs}>
                  {t('common.refresh')}
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Spin>
      )}

      <Divider orientation="left">{t('pluginConfig.customPlugins')}</Divider>
      <Space style={{ marginBottom: 16 }}>
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCustomPluginModal()}>
            {t('pluginConfig.addCustomPlugin')}
          </Button>
        )}
        <Button icon={<ReloadOutlined />} onClick={loadCustomPlugins}>
          {t('common.refresh')}
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
        title={editingPlugin ? t('pluginConfig.editCustomPlugin') : t('pluginConfig.addCustomPlugin')}
        open={customPluginModalOpen}
        onCancel={() => {
          setCustomPluginModalOpen(false);
          customPluginForm.resetFields();
          setEditingPlugin(null);
        }}
        onOk={handleCustomPluginSubmit}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={customPluginForm} layout="vertical">
          <Form.Item label={t('common.name')} name="name" rules={[{ required: true, message: t('pluginConfig.validationNameRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label="Slug" name="slug" rules={[{ required: true, message: t('pluginConfig.validationSlugRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label={t('common.description')} name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label={t('pluginConfig.pluginUrl')} name="pluginUrl" rules={[{ required: true, message: t('pluginConfig.validationPluginUrlRequired') }]}> 
            <Input />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey">
            <Input.Password placeholder={t('common.keepEmptyToKeepValue')} />
          </Form.Item>
          <Form.Item label={t('pluginConfig.authType')} name="authType" initialValue="bearer">
            <Select
              options={[
                { label: 'bearer', value: 'bearer' },
                { label: 'header', value: 'header' },
                { label: 'query', value: 'query' },
              ]}
            />
          </Form.Item>
          <Form.Item label={t('pluginConfig.authHeader')} name="authHeader" initialValue="Authorization">
            <Input />
          </Form.Item>
          <Form.Item label={t('pluginConfig.parserType')} name="parserType" initialValue="json">
            <Select
              options={[
                { label: 'json', value: 'json' },
                { label: 'xml', value: 'xml' },
                { label: 'regex', value: 'regex' },
              ]}
            />
          </Form.Item>
          <Form.Item label={t('pluginConfig.requestMethod')} name="requestMethod" initialValue="GET">
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
                  <Form.Item label={t('pluginConfig.requestBody')} name="requestBody">
                    <Input.TextArea rows={3} />
                  </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item label={t('pluginConfig.responsePath')} name="responsePath">
            <Input />
          </Form.Item>
          <Form.Item label={t('common.enabled')} name="enabled" valuePropName="checked" initialValue>
            <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
          </Form.Item>
          <Form.Item label={t('common.priority')} name="priority" initialValue={50}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={t('common.timeoutSeconds')} name="timeout" initialValue={30}>
            <InputNumber min={5} max={300} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default PluginConfig;
