import { Card, Tabs } from 'antd';
import { SettingOutlined, BellOutlined, ApiOutlined, RobotOutlined, AppstoreOutlined } from '@ant-design/icons';
import GeneralConfig from './GeneralConfig';
import NotificationConfig from './NotificationConfig';
import IntegrationConfig from './IntegrationConfig';
import AIConfig from './AIConfig';
import PluginConfig from './PluginConfig';

const Configuration = () => {
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
          <SettingOutlined /> 基础设置
        </span>
      ),
      children: <GeneralConfig />,
    },
    {
      key: 'notification',
      label: (
        <span>
          <BellOutlined /> 通知配置
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
                <ApiOutlined /> 集成配置
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
                <RobotOutlined /> AI配置
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
                <AppstoreOutlined /> 插件配置
              </span>
            ),
            children: <PluginConfig />,
          },
        ]
      : []),
  ];

  return (
    <Card title="系统设置" bordered={false}>
      <Tabs defaultActiveKey="general" items={tabItems} />
    </Card>
  );
};

export default Configuration;
