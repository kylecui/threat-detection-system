import { BrowserRouter, Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { useMemo } from 'react';
import { ProLayout } from '@ant-design/pro-components';
import { Button, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import {
  DashboardOutlined,
  WarningOutlined,
  SettingOutlined,
  BellOutlined,
  TeamOutlined,
  GlobalOutlined,
  ExperimentOutlined,
  CloudServerOutlined,
  LogoutOutlined,
  ApartmentOutlined,
  UserOutlined,
  SearchOutlined,
  ToolOutlined,
  ApiOutlined,
  RobotOutlined,
  AppstoreOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { AuthProvider, useAuth, type AppRole } from './contexts/AuthContext';
import { ScopeProvider } from './contexts/ScopeContext';
import { useTheme } from './contexts/ThemeContext';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import RouteGuard from './components/RouteGuard';
import ErrorBoundary from './components/ErrorBoundary';
import ScopeSelector from './components/ScopeSelector';
import Overview from './pages/Overview';
import ThreatList from './pages/ThreatList';
import AlertCenter from './pages/AlertCenter';
import CustomersAndDevices from './pages/CustomersAndDevices';
import ThreatIntel from './pages/ThreatIntel';
import MlDetection from './pages/MlDetection';
import PipelineHealth from './pages/PipelineHealth';
import LoginPage from './pages/Login';
import TenantMgmt from './pages/TenantMgmt';
import UserMgmt from './pages/UserMgmt';
import GeneralConfig from './pages/Configuration/GeneralConfig';
import NotificationConfig from './pages/Configuration/NotificationConfig';
import IntegrationConfig from './pages/Configuration/IntegrationConfig';
import AIConfig from './pages/Configuration/AIConfig';
import PluginConfig from './pages/Configuration/PluginConfig';

interface MenuRoute {
  path: string;
  name: string;
  icon?: React.ReactNode;
  requiredRoles?: AppRole[];
  routes?: MenuRoute[];
}

const getAllMenuRoutes = (t: TFunction): MenuRoute[] => [
  { path: '/overview', name: t('nav.overview'), icon: <DashboardOutlined /> },
  {
    path: '/investigate',
    name: t('nav.investigate'),
    icon: <SearchOutlined />,
    routes: [
      { path: '/investigate/alerts', name: t('nav.alerts'), icon: <BellOutlined /> },
      { path: '/investigate/threats', name: t('nav.threats'), icon: <WarningOutlined /> },
      { path: '/investigate/intel', name: t('nav.intel'), icon: <GlobalOutlined /> },
    ],
  },
  {
    path: '/operate',
    name: t('nav.operate'),
    icon: <ToolOutlined />,
    routes: [
      {
        path: '/operate/pipeline',
        name: t('nav.pipeline'),
        icon: <CloudServerOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/operate/ml',
        name: t('nav.ml'),
        icon: <ExperimentOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
    ],
  },
  {
    path: '/admin',
    name: t('nav.admin'),
    icon: <TeamOutlined />,
    routes: [
      {
        path: '/admin/customers',
        name: t('nav.customersDevices'),
        icon: <TeamOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/admin/users',
        name: t('nav.users'),
        icon: <UserOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/admin/tenants',
        name: t('nav.tenants'),
        icon: <ApartmentOutlined />,
        requiredRoles: ['SUPER_ADMIN'],
      },
    ],
  },
  {
    path: '/config',
    name: t('nav.config'),
    icon: <SettingOutlined />,
    routes: [
      {
        path: '/config/general',
        name: t('nav.generalConfig'),
        icon: <SettingOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      { path: '/config/notifications', name: t('nav.notificationConfig'), icon: <BellOutlined /> },
      {
        path: '/config/integrations',
        name: t('nav.integrationConfig'),
        icon: <ApiOutlined />,
        requiredRoles: ['SUPER_ADMIN'],
      },
      { path: '/config/ai', name: t('nav.aiConfig'), icon: <RobotOutlined /> },
      {
        path: '/config/plugins',
        name: t('nav.pluginConfig'),
        icon: <AppstoreOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
    ],
  },
];

function filterMenuRoutes(routes: MenuRoute[], roles: AppRole[], isSuperAdmin: boolean): MenuRoute[] {
  return routes
    .map((route) => {
      const filteredChildren = route.routes
        ? filterMenuRoutes(route.routes, roles, isSuperAdmin)
        : undefined;

      const hasAccess = !route.requiredRoles
        || isSuperAdmin
        || route.requiredRoles.some((r) => roles.includes(r));

      if (!hasAccess) return null;
      if (route.routes && filteredChildren && filteredChildren.length === 0) return null;

      return { ...route, routes: filteredChildren };
    })
    .filter(Boolean) as MenuRoute[];
}

function AppLayout() {
  const { user, logout, isSuperAdmin } = useAuth();
  const { isDarkMode, toggleTheme } = useTheme();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const roles = user?.roles ?? [];

  const menuRoutes = useMemo(() => {
    const allMenuRoutes = getAllMenuRoutes(t);
    return filterMenuRoutes(allMenuRoutes, roles, isSuperAdmin);
  }, [t, roles, isSuperAdmin]);

  const currentLang = i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN';

  return (
    <ConfigProvider
      locale={currentLang === 'en-US' ? enUS : zhCN}
      theme={{
        algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1890ff',
          borderRadius: 6,
        },
      }}
    >
      <ProLayout
        title={t('app.title')}
        logo={<DashboardOutlined />}
        layout="mix"
        fixedHeader
        fixSiderbar
        avatarProps={{
          title: user?.displayName || user?.username || t('app.user'),
          size: 'small',
          render: (_props, dom) => (
            <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {dom}
              <Button
                type="text"
                size="small"
                icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleTheme}
                aria-label={t('app.toggleTheme')}
              />
              <Button
                type="text"
                size="small"
                onClick={() => i18n.changeLanguage(currentLang === 'zh-CN' ? 'en-US' : 'zh-CN')}
                icon={<GlobalOutlined />}
                aria-label={t('app.switchLanguage')}
              >
                {currentLang === 'zh-CN' ? 'EN' : '中'}
              </Button>
              <ScopeSelector />
              <Button
                type="text"
                size="small"
                icon={<LogoutOutlined />}
                onClick={logout}
                style={{ color: isDarkMode ? 'rgba(255,255,255,0.45)' : 'rgba(0,0,0,0.45)' }}
                aria-label={t('app.logout')}
              />
            </span>
          ),
        }}
        route={{
          path: '/',
          routes: menuRoutes,
        }}
        location={{ pathname: location.pathname }}
        breadcrumbRender={(routers = []) => routers}
        menuItemRender={(item, dom) => (
          <div onClick={() => navigate(item.path || '/')} style={{ cursor: 'pointer' }}>
            {dom}
          </div>
        )}
      >
        <ErrorBoundary>
          <Routes>
          <Route path="/" element={<Navigate to="/overview" replace />} />
          <Route path="/overview" element={<Overview />} />

          <Route path="/investigate" element={<Navigate to="/investigate/alerts" replace />} />
          <Route path="/investigate/alerts" element={<AlertCenter />} />
          <Route path="/investigate/threats" element={<ThreatList />} />
          <Route path="/investigate/intel" element={<ThreatIntel />} />

          <Route path="/operate" element={<Navigate to="/operate/pipeline" replace />} />
          <Route
            path="/operate/pipeline"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <PipelineHealth />
              </RouteGuard>
            )}
          />
          <Route
            path="/operate/ml"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <MlDetection />
              </RouteGuard>
            )}
          />

          <Route path="/admin" element={<Navigate to="/admin/customers" replace />} />
          <Route
            path="/admin/customers"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <CustomersAndDevices />
              </RouteGuard>
            )}
          />
          <Route
            path="/admin/users"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <UserMgmt />
              </RouteGuard>
            )}
          />
          <Route
            path="/admin/tenants"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN']}>
                <TenantMgmt />
              </RouteGuard>
            )}
          />

          <Route path="/config" element={<Navigate to="/config/general" replace />} />
          <Route
            path="/config/general"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <GeneralConfig />
              </RouteGuard>
            )}
          />
          <Route path="/config/notifications" element={<NotificationConfig />} />
          <Route
            path="/config/integrations"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN']}>
                <IntegrationConfig />
              </RouteGuard>
            )}
          />
          <Route path="/config/ai" element={<AIConfig />} />
          <Route
            path="/config/plugins"
            element={(
              <RouteGuard requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
                <PluginConfig />
              </RouteGuard>
            )}
          />

          <Route path="/threats" element={<Navigate to="/investigate/threats" replace />} />
          <Route path="/alerts" element={<Navigate to="/investigate/alerts" replace />} />
          <Route path="/threat-intel" element={<Navigate to="/investigate/intel" replace />} />
          <Route path="/pipeline" element={<Navigate to="/operate/pipeline" replace />} />
          <Route path="/ml" element={<Navigate to="/operate/ml" replace />} />
          <Route path="/customers-devices" element={<Navigate to="/admin/customers" replace />} />
          <Route path="/tenants" element={<Navigate to="/admin/tenants" replace />} />
          <Route path="/users" element={<Navigate to="/admin/users" replace />} />
          <Route path="/configuration" element={<Navigate to="/config/general" replace />} />
        </Routes>
        </ErrorBoundary>
      </ProLayout>
    </ConfigProvider>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/*" element={
            <RouteGuard>
              <ScopeProvider>
                <AppLayout />
              </ScopeProvider>
            </RouteGuard>
          } />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
