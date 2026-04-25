import { BrowserRouter, Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import { Button, ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
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

const ALL_MENU_ROUTES: MenuRoute[] = [
  { path: '/overview', name: '威胁总览', icon: <DashboardOutlined /> },
  {
    path: '/investigate',
    name: '调查',
    icon: <SearchOutlined />,
    routes: [
      { path: '/investigate/alerts', name: '告警中心', icon: <BellOutlined /> },
      { path: '/investigate/threats', name: '威胁列表', icon: <WarningOutlined /> },
      { path: '/investigate/intel', name: '威胁情报', icon: <GlobalOutlined /> },
    ],
  },
  {
    path: '/operate',
    name: '运维',
    icon: <ToolOutlined />,
    routes: [
      {
        path: '/operate/pipeline',
        name: '管道健康',
        icon: <CloudServerOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/operate/ml',
        name: 'ML检测',
        icon: <ExperimentOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
    ],
  },
  {
    path: '/admin',
    name: '管理',
    icon: <TeamOutlined />,
    routes: [
      {
        path: '/admin/customers',
        name: '客户与设备',
        icon: <TeamOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/admin/users',
        name: '用户管理',
        icon: <UserOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      {
        path: '/admin/tenants',
        name: '租户管理',
        icon: <ApartmentOutlined />,
        requiredRoles: ['SUPER_ADMIN'],
      },
    ],
  },
  {
    path: '/config',
    name: '配置',
    icon: <SettingOutlined />,
    routes: [
      {
        path: '/config/general',
        name: '基础设置',
        icon: <SettingOutlined />,
        requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'],
      },
      { path: '/config/notifications', name: '通知配置', icon: <BellOutlined /> },
      {
        path: '/config/integrations',
        name: '集成配置',
        icon: <ApiOutlined />,
        requiredRoles: ['SUPER_ADMIN'],
      },
      { path: '/config/ai', name: 'AI配置', icon: <RobotOutlined /> },
      {
        path: '/config/plugins',
        name: '插件配置',
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
  const navigate = useNavigate();
  const location = useLocation();
  const roles = user?.roles ?? [];

  const menuRoutes = filterMenuRoutes(ALL_MENU_ROUTES, roles, isSuperAdmin);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1890ff',
          borderRadius: 6,
        },
      }}
    >
      <ProLayout
        title="威胁检测系统"
        logo={<DashboardOutlined />}
        layout="mix"
        fixedHeader
        fixSiderbar
        avatarProps={{
          title: user?.displayName || user?.username || 'User',
          size: 'small',
          render: (_props, dom) => (
            <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {dom}
              <Button
                type="text"
                size="small"
                icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleTheme}
              />
              <ScopeSelector />
              <Button
                type="text"
                size="small"
                icon={<LogoutOutlined />}
                onClick={logout}
                style={{ color: 'rgba(0,0,0,0.45)' }}
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
          <Route path="/operate/pipeline" element={<PipelineHealth />} />
          <Route path="/operate/ml" element={<MlDetection />} />

          <Route path="/admin" element={<Navigate to="/admin/customers" replace />} />
          <Route path="/admin/customers" element={<CustomersAndDevices />} />
          <Route path="/admin/users" element={<UserMgmt />} />
          <Route path="/admin/tenants" element={<TenantMgmt />} />

          <Route path="/config" element={<Navigate to="/config/general" replace />} />
          <Route path="/config/general" element={<GeneralConfig />} />
          <Route path="/config/notifications" element={<NotificationConfig />} />
          <Route path="/config/integrations" element={<IntegrationConfig />} />
          <Route path="/config/ai" element={<AIConfig />} />
          <Route path="/config/plugins" element={<PluginConfig />} />

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
