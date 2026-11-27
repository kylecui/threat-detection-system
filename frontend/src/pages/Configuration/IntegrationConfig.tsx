import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Space,
  Spin,
  Tag,
  message,
} from 'antd';
import {
  EyeInvisibleOutlined,
  EyeOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { batchUpdateConfigs, getConfigsByCategory } from '@/services/config';
import { usePermission } from '@/hooks/usePermission';
import type { SystemConfig } from '@/types';
import './configuration.css';

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
      [...apiKeys, ...general].forEach((config) => {
        formValues[config.key] = config.isSecret ? '' : config.value;
      });
      tireForm.setFieldsValue(formValues);
    } catch {
      console.log('Failed to load TIRE configs');
    } finally {
      setTireLoading(false);
    }
  }, [isSuperAdmin, tireForm]);

  useEffect(() => {
    void loadTireConfigs();
  }, [loadTireConfigs]);

  const handleTireSave = async (values: Record<string, string>) => {
    try {
      setTireSaving(true);
      const updates: Record<string, string> = {};
      const allTireConfigs = [...tireConfigs, ...tireGeneralConfigs];
      Object.entries(values).forEach(([key, value]) => {
        const configDefinition = allTireConfigs.find((config) => config.key === key);
        if (configDefinition?.isSecret && !value) return;
        if (value !== undefined && value !== null) updates[key] = value;
      });
      if (Object.keys(updates).length > 0) {
        await batchUpdateConfigs(updates);
      }
      message.success(t('integrationConfig.saved'));
      setRevealedKeys(new Set());
      void loadTireConfigs();
    } catch {
      message.error(t('integrationConfig.saveFailed'));
    } finally {
      setTireSaving(false);
    }
  };

  const toggleReveal = (key: string) => {
    setRevealedKeys((previous) => {
      const next = new Set(previous);
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
          <Space wrap>
            {config.description || config.key}
            {config.isSecret && config.hasValue ? (
              <Tag color="green">{t('common.configured')}</Tag>
            ) : null}
            {config.isSecret && !config.hasValue ? (
              <Tag color="orange">{t('common.notConfigured')}</Tag>
            ) : null}
          </Space>
        }
        name={config.key}
        tooltip={config.key}
      >
        {config.isSecret ? (
          <Input
            placeholder={
              config.hasValue
                ? t('common.keepEmptyToKeepValue')
                : t('integrationConfig.enterApiKey')
            }
            type={isRevealed ? 'text' : 'password'}
            suffix={
              <Button
                type="text"
                size="small"
                aria-label={t(
                  isRevealed ? 'common.hideSecret' : 'common.showSecret',
                )}
                icon={isRevealed ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                onClick={() => toggleReveal(config.key)}
              />
            }
          />
        ) : (
          <Input
            placeholder={t('integrationConfig.enterConfigValue', {
              key: config.description || config.key,
            })}
          />
        )}
      </Form.Item>
    );
  };

  return (
    <div className="tds-config-page tds-config-integrations">
      <Alert
        className="tds-config-note"
        message={t('integrationConfig.title')}
        description={t('integrationConfig.description')}
        type="info"
        showIcon
      />

      <section className="tds-config-panel tds-config-panel--form">
        <Spin spinning={tireLoading}>
          <Form
            className="tds-config-form tds-config-form--wide"
            form={tireForm}
            layout="vertical"
            onFinish={handleTireSave}
          >
            {tireConfigs.length > 0 ? (
              <section className="tds-config-field-group">
                <h2>{t('integrationConfig.apiKeys')}</h2>
                {tireConfigs.map(renderConfigField)}
              </section>
            ) : null}
            {tireGeneralConfigs.length > 0 ? (
              <section className="tds-config-field-group">
                <h2>{t('integrationConfig.generalSettings')}</h2>
                {tireGeneralConfigs.map(renderConfigField)}
              </section>
            ) : null}
            {tireConfigs.length === 0 && tireGeneralConfigs.length === 0 ? (
              <Alert
                className="tds-config-inline-state"
                message={t('integrationConfig.noConfigItems')}
                description={t('integrationConfig.initHint')}
                type="warning"
                showIcon
              />
            ) : null}
            <div className="tds-config-actions">
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
            </div>
          </Form>
        </Spin>
      </section>
    </div>
  );
};

export default IntegrationConfig;
