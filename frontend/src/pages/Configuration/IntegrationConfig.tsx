import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  message,
  Alert,
  Space,
  Divider,
  Spin,
  Typography,
  Tag,
} from 'antd';
import {
  SaveOutlined,
  ReloadOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
} from '@ant-design/icons';
import { getConfigsByCategory, batchUpdateConfigs } from '@/services/config';
import type { SystemConfig } from '@/types';

const { Text } = Typography;

const IntegrationConfig = () => {
  const [tireForm] = Form.useForm();
  const [tireConfigs, setTireConfigs] = useState<SystemConfig[]>([]);
  const [tireGeneralConfigs, setTireGeneralConfigs] = useState<SystemConfig[]>([]);
  const [tireLoading, setTireLoading] = useState(false);
  const [tireSaving, setTireSaving] = useState(false);
  const [revealedKeys, setRevealedKeys] = useState<Set<string>>(new Set());

  const userRoles: string[] = (() => {
    try {
      const user = localStorage.getItem('user');
      return user ? JSON.parse(user).roles || [] : [];
    } catch { return []; }
  })();
  const isSuperAdmin = userRoles.includes('SUPER_ADMIN');

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

  useEffect(() => {
    loadTireConfigs();
  }, [loadTireConfigs]);

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

  return (
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
  );
};

export default IntegrationConfig;
