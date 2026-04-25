import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import { Button } from 'antd';
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
} from '@ant-design/icons';
import { AuthProvider, useAuth, type AppRole } from './contexts/AuthContext';
import { ScopeProvider } from './contexts/ScopeContext';
import RouteGuard from './components/RouteGuard';
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
import Configuration from './pages/Configuration';

interface MenuRoute {
  path: string;
  name: string;
  icon: React.ReactNode;
  requiredRoles?: AppRole[];
}

const ALL_MENU_ROUTES: MenuRoute[] = [
  { path: '/overview', name: '威胁总览', icon: <DashboardOutlined /> },
  { path: '/threats', name: '威胁列表', icon: <WarningOutlined /> },
  { path: '/alerts', name: '告警中心', icon: <BellOutlined /> },
  { path: '/customers-devices', name: '客户与设备', icon: <TeamOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/threat-intel', name: '威胁情报', icon: <GlobalOutlined /> },
  { path: '/ml', name: 'ML检测', icon: <ExperimentOutlined /> },
  { path: '/pipeline', name: '管道健康', icon: <CloudServerOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/tenants', name: '租户管理', icon: <ApartmentOutlined />, requiredRoles: ['SUPER_ADMIN'] },
  { path: '/users', name: '用户管理', icon: <UserOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/configuration', name: '系统配置', icon: <SettingOutlined /> },
];

function AppLayout() {
  const { user, logout, isSuperAdmin } = useAuth();
  const roles = user?.roles ?? [];

  const menuRoutes = ALL_MENU_ROUTES.filter((route) => {
    if (!route.requiredRoles) return true;
    if (isSuperAdmin) return true;
    return route.requiredRoles.some((r) => roles.includes(r));
  });

  return (
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
      menuItemRender={(item, dom) => (
        <a onClick={() => { window.location.href = item.path || '/'; }}>
          {dom}
        </a>
      )}
    >
      <Routes>
        <Route path="/" element={<Navigate to="/overview" replace />} />
        <Route path="/overview" element={<Overview />} />
        <Route path="/threats" element={<ThreatList />} />
        <Route path="/alerts" element={<AlertCenter />} />
        <Route path="/customers-devices" element={<CustomersAndDevices />} />
        <Route path="/threat-intel" element={<ThreatIntel />} />
        <Route path="/ml" element={<MlDetection />} />
        <Route path="/pipeline" element={<PipelineHealth />} />
        <Route path="/tenants" element={<TenantMgmt />} />
        <Route path="/users" element={<UserMgmt />} />
        <Route path="/configuration" element={<Configuration />} />
      </Routes>
    </ProLayout>
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
