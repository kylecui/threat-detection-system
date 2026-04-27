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
import { useTranslation } from 'react-i18next';
import { getConfigsByCategory, batchUpdateConfigs } from '@/services/config';
import { usePermission } from '@/hooks/usePermission';
import type { SystemConfig } from '@/types';

const { Text } = Typography;

const IntegrationConfig = () => {
  const { t } = useTranslation();
  const [tireForm] = Form.useForm();
  const [tireConfigs, setTireConfigs] = useState<SystemConfig[]>([]);
  const [tireGeneralConfigs, setTireGeneralConfigs] = useState<SystemConfig[]>([]);
  const [tireLoading, setTireLoading] = useState(false);
  const [tireSaving, setTireSaving] = useState(false);
  const [revealedKeys, setRevealedKeys] = useState<Set<string>>(new Set());

  const { isSuperAdmin } = usePermission();

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
      message.success(t('integrationConfig.saved'));
      setRevealedKeys(new Set());
      loadTireConfigs();
    } catch {
      message.error(t('integrationConfig.saveFailed'));
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
            placeholder={config.hasValue ? t('common.keepEmptyToKeepValue') : t('integrationConfig.enterApiKey')}
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
          <Input placeholder={t('integrationConfig.enterConfigValue', { key: config.description || config.key })} />
        )}
      </Form.Item>
    );
  };

  return (
    <Card bordered={false}>
      <Spin spinning={tireLoading}>
        <Alert
          message={t('integrationConfig.title')}
          description={t('integrationConfig.description')}
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
                <Text strong>{t('integrationConfig.apiKeys')}</Text>
              </Divider>
              {tireConfigs.map(renderConfigField)}
            </>
          )}
          {tireGeneralConfigs.length > 0 && (
            <>
              <Divider orientation="left">
                <Text strong>{t('integrationConfig.generalSettings')}</Text>
              </Divider>
              {tireGeneralConfigs.map(renderConfigField)}
            </>
          )}
          {tireConfigs.length === 0 && tireGeneralConfigs.length === 0 && (
            <Alert
              message={t('integrationConfig.noConfigItems')}
              description={t('integrationConfig.initHint')}
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
                  {t('integrationConfig.save')}
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadTireConfigs}>
                  {t('common.refresh')}
                </Button>
            </Space>
          </Form.Item>
        </Form>
      </Spin>
    </Card>
  );
};

export default IntegrationConfig;
