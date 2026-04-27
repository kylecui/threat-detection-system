import { Card, Tabs } from 'antd';
import { SettingOutlined, BellOutlined, ApiOutlined, RobotOutlined, AppstoreOutlined, TeamOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import GeneralConfig from './GeneralConfig';
import NotificationConfig from './NotificationConfig';
import IntegrationConfig from './IntegrationConfig';
import AIConfig from './AIConfig';
import ConfigCascading from './ConfigCascading';
import PluginConfig from './PluginConfig';

const Configuration = () => {
  const { t } = useTranslation();
  const { isSuperAdmin, isTenantAdmin, isCustomerUser, isAdminRole: isAdmin } = usePermission();
  const canAccessLlmTab = isSuperAdmin || isTenantAdmin || isCustomerUser;

  const tabItems = [
    {
      key: 'general',
      label: (
        <span>
          <SettingOutlined /> {t('nav.generalConfig')}
        </span>
      ),
      children: <GeneralConfig />,
    },
    {
      key: 'notification',
      label: (
        <span>
          <BellOutlined /> {t('nav.notificationConfig')}
        </span>
      ),
      children: <NotificationConfig />,
    },
    ...(isSuperAdmin
      ? [
          {
            key: 'integration',
            label: (
              <span>
                <ApiOutlined /> {t('nav.integrationConfig')}
              </span>
            ),
            children: <IntegrationConfig />,
          },
        ]
      : []),
    ...(canAccessLlmTab
      ? [
          {
            key: 'ai',
            label: (
              <span>
                <RobotOutlined /> {t('nav.aiConfig')}
              </span>
            ),
            children: <AIConfig />,
          },
        ]
      : []),
    ...(isAdmin
      ? [
          {
            key: 'cascading',
            label: (
              <span>
                <TeamOutlined /> {t('nav.configCascading')}
              </span>
            ),
            children: <ConfigCascading />,
          },
        ]
      : []),
    ...(isAdmin
      ? [
          {
            key: 'plugin',
            label: (
              <span>
                <AppstoreOutlined /> {t('nav.pluginConfig')}
              </span>
            ),
            children: <PluginConfig />,
          },
        ]
      : []),
  ];

  return (
    <Card title={t('configuration.title')} bordered={false}>
      <Tabs defaultActiveKey="general" items={tabItems} />
    </Card>
  );
};

export default Configuration;
