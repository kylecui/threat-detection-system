import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import { Button } from 'antd';
import {
  DashboardOutlined,
  WarningOutlined,
  BarChartOutlined,
  SettingOutlined,
  BellOutlined,
  TeamOutlined,
  GlobalOutlined,
  ExperimentOutlined,
  CloudServerOutlined,
  LogoutOutlined,
  ApartmentOutlined,
  UserOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import { AuthProvider, useAuth, type AppRole } from './contexts/AuthContext';
import { ScopeProvider } from './contexts/ScopeContext';
import RouteGuard from './components/RouteGuard';
import ScopeSelector from './components/ScopeSelector';
import Dashboard from './pages/Dashboard';
import ThreatList from './pages/ThreatList';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';
import AlertCenter from './pages/AlertCenter';
import CustomerMgmt from './pages/CustomerMgmt';
import ThreatIntel from './pages/ThreatIntel';
import MlDetection from './pages/MlDetection';
import PipelineHealth from './pages/PipelineHealth';
import LoginPage from './pages/Login';
import TenantMgmt from './pages/TenantMgmt';
import UserMgmt from './pages/UserMgmt';
import DeviceMgmt from './pages/DeviceMgmt';

interface MenuRoute {
  path: string;
  name: string;
  icon: React.ReactNode;
  requiredRoles?: AppRole[];
}

const ALL_MENU_ROUTES: MenuRoute[] = [
  { path: '/dashboard', name: '仪表盘', icon: <DashboardOutlined /> },
  { path: '/threats', name: '威胁列表', icon: <WarningOutlined /> },
  { path: '/alerts', name: '告警中心', icon: <BellOutlined /> },
  { path: '/analytics', name: '数据分析', icon: <BarChartOutlined /> },
  { path: '/customers', name: '客户管理', icon: <TeamOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/devices', name: '设备管理', icon: <ApiOutlined /> },
  { path: '/threat-intel', name: '威胁情报', icon: <GlobalOutlined /> },
  { path: '/ml', name: 'ML检测', icon: <ExperimentOutlined /> },
  { path: '/pipeline', name: '管道健康', icon: <CloudServerOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/tenants', name: '租户管理', icon: <ApartmentOutlined />, requiredRoles: ['SUPER_ADMIN'] },
  { path: '/users', name: '用户管理', icon: <UserOutlined />, requiredRoles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
  { path: '/settings', name: '系统设置', icon: <SettingOutlined /> },
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
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/threats" element={<ThreatList />} />
        <Route path="/alerts" element={<AlertCenter />} />
        <Route path="/analytics" element={<Analytics />} />
        <Route path="/customers" element={<CustomerMgmt />} />
        <Route path="/devices" element={<DeviceMgmt />} />
        <Route path="/threat-intel" element={<ThreatIntel />} />
        <Route path="/ml" element={<MlDetection />} />
        <Route path="/pipeline" element={<PipelineHealth />} />
        <Route path="/tenants" element={<TenantMgmt />} />
        <Route path="/users" element={<UserMgmt />} />
        <Route path="/settings" element={<Settings />} />
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
