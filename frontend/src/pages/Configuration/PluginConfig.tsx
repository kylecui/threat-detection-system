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
import useSubmitGuard from '@/hooks/useSubmitGuard';
import TableActionButton from '@/components/TableActionButton';
import TableActions from '@/components/TableActions';
import TableCellText from '@/components/TableCellText';
import './configuration.css';

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
  const customPluginMutation = useSubmitGuard();

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
  }, [isAdmin, t]);

  useEffect(() => {
    loadPluginConfigs();
    loadCustomPlugins();
  }, [loadCustomPlugins, loadPluginConfigs]);

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
    if (!customPluginMutation.begin()) return;
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
      // Validation errors remain visible on their corresponding form fields.
    } finally {
      customPluginMutation.end();
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
      width: 180,
      render: (name: string) => <TableCellText value={name} />,
    },
    {
      title: 'Slug',
      dataIndex: 'slug',
      key: 'slug',
      width: 160,
      render: (slug: string) => <TableCellText value={slug} />,
    },
    {
      title: t('pluginConfig.pluginUrl'),
      dataIndex: 'pluginUrl',
      key: 'pluginUrl',
      width: 280,
      render: (pluginUrl: string) => <TableCellText value={pluginUrl} />,
    },
    {
      title: t('pluginConfig.requestMethod'),
      dataIndex: 'requestMethod',
      key: 'requestMethod',
      width: 120,
    },
    {
      title: t('common.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 96,
      render: (enabled: boolean) => (enabled ? <Tag color="green">{t('common.on')}</Tag> : <Tag color="red">{t('common.off')}</Tag>),
    },
    {
      title: t('common.priority'),
      dataIndex: 'priority',
      key: 'priority',
      width: 96,
    },
    {
      title: t('common.timeoutSeconds'),
      dataIndex: 'timeout',
      key: 'timeout',
      width: 136,
    },
    {
      title: t('common.owner'),
      dataIndex: 'ownerType',
      key: 'ownerType',
      width: 144,
      render: (ownerType: TireCustomPlugin['ownerType']) => {
        if (ownerType === 'SYSTEM') return <Tag color="blue">SYSTEM</Tag>;
        if (ownerType === 'TENANT') return <Tag color="green">TENANT</Tag>;
        return <Tag color="orange">USER</Tag>;
      },
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 108,
      fixed: 'right',
      render: (_: unknown, record: TireCustomPlugin) => (
        <TableActions label={t('common.actions')}>
          <TableActionButton
            label={t('common.edit')}
            icon={<EditOutlined />}
            onClick={() => openCustomPluginModal(record)}
          />
          <TableActionButton
            label={t('common.delete')}
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteCustomPlugin(record)}
          />
        </TableActions>
      ),
    },
  ];

  return (
    <div className="tds-config-page tds-config-plugins">
      {isSuperAdmin && (
        <section className="tds-config-panel tds-config-panel--form">
          <Spin spinning={pluginLoading}>
            <Alert
              className="tds-config-note"
              message={t('pluginConfig.title')}
              type="info"
              showIcon
            />
            <Form
              className="tds-config-form tds-config-form--full"
              form={pluginForm}
              layout="vertical"
              onFinish={handlePluginSave}
            >
              <div className="tds-config-plugin-grid tds-config-plugin-grid--header">
              <Text type="secondary">{t('pluginConfig.pluginName')}</Text>
              <Text type="secondary">{t('pluginConfig.enabledState')}</Text>
              <Text type="secondary">{t('common.priority')}</Text>
              <Text type="secondary">{t('common.timeoutSeconds')}</Text>
              </div>
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                {pluginRows.map((row) => (
                  <Card className="tds-config-plugin-record" key={row.pluginName} size="small">
                    <div className="tds-config-plugin-grid">
                    <Text strong>{row.label}</Text>
                      <div className="tds-config-plugin-field">
                        <Text className="tds-config-plugin-label" type="secondary">{t('pluginConfig.enabledState')}</Text>
                        <Form.Item name={row.enabledKey} valuePropName="checked" style={{ marginBottom: 0 }}>
                          <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
                        </Form.Item>
                      </div>
                      <div className="tds-config-plugin-field">
                        <Text className="tds-config-plugin-label" type="secondary">{t('common.priority')}</Text>
                        <Form.Item name={row.priorityKey} style={{ marginBottom: 0 }}>
                          <InputNumber min={1} max={100} style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                      <div className="tds-config-plugin-field">
                        <Text className="tds-config-plugin-label" type="secondary">{t('common.timeoutSeconds')}</Text>
                        <Form.Item name={row.timeoutKey} style={{ marginBottom: 0 }}>
                          <InputNumber min={5} max={300} addonAfter={t('common.secondShort')} style={{ width: '100%' }} />
                        </Form.Item>
                      </div>
                    </div>
                  </Card>
                ))}
              </Space>
              {pluginRows.length === 0 && (
                <Alert
                  className="tds-config-inline-state"
                  message={t('pluginConfig.noConfigItems')}
                  description={t('pluginConfig.initHint')}
                  type="warning"
                />
              )}
              <Form.Item style={{ marginTop: 24 }}>
                <Space className="tds-config-actions" wrap>
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
        </section>
      )}

      <section className="tds-config-panel tds-config-panel--form">
        <Divider orientation="left">{t('pluginConfig.customPlugins')}</Divider>
        <Space className="tds-config-actions" wrap>
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
          tableLayout="fixed"
          scroll={{ x: 1320 }}
        />
      </section>

      <Modal
        title={editingPlugin ? t('pluginConfig.editCustomPlugin') : t('pluginConfig.addCustomPlugin')}
        open={customPluginModalOpen}
        onCancel={() => {
          if (customPluginMutation.submitting) return;
          setCustomPluginModalOpen(false);
          customPluginForm.resetFields();
          setEditingPlugin(null);
        }}
        onOk={handleCustomPluginSubmit}
        confirmLoading={customPluginMutation.submitting}
        cancelButtonProps={{ disabled: customPluginMutation.submitting }}
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
    </div>
  );
};

export default PluginConfig;
