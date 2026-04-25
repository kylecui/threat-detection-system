import { Card, Tabs } from 'antd';
import { SettingOutlined, BellOutlined, ApiOutlined, RobotOutlined, AppstoreOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import GeneralConfig from './GeneralConfig';
import NotificationConfig from './NotificationConfig';
import IntegrationConfig from './IntegrationConfig';
import AIConfig from './AIConfig';
import PluginConfig from './PluginConfig';

const Configuration = () => {
  const { t } = useTranslation();
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
