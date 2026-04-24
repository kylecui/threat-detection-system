import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
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
} from '@ant-design/icons';
import Dashboard from './pages/Dashboard';
import ThreatList from './pages/ThreatList';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';
import AlertCenter from './pages/AlertCenter';
import CustomerMgmt from './pages/CustomerMgmt';
import ThreatIntel from './pages/ThreatIntel';
import MlDetection from './pages/MlDetection';
import SystemMonitor from './pages/SystemMonitor';
import LoginPage from './pages/Login';
import TenantMgmt from './pages/TenantMgmt';
import UserMgmt from './pages/UserMgmt';

function isAuthenticated(): boolean {
  return !!localStorage.getItem('token');
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  if (!isAuthenticated()) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

function handleLogout() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  localStorage.removeItem('customer_id');
  window.location.href = '/login';
}

function getCurrentUser(): { displayName?: string; username?: string; roles?: string[] } | null {
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function AppLayout() {
  const user = getCurrentUser();
  const roles = user?.roles || [];
  const isSuperAdmin = roles.includes('SUPER_ADMIN');
  const isTenantAdmin = roles.includes('TENANT_ADMIN');
  const isAdminRole = isSuperAdmin || isTenantAdmin;

  const menuRoutes = [
    { path: '/dashboard', name: '仪表盘', icon: <DashboardOutlined /> },
    { path: '/threats', name: '威胁列表', icon: <WarningOutlined /> },
    { path: '/alerts', name: '告警中心', icon: <BellOutlined /> },
    { path: '/analytics', name: '数据分析', icon: <BarChartOutlined /> },
    { path: '/customers', name: '客户管理', icon: <TeamOutlined /> },
    { path: '/threat-intel', name: '威胁情报', icon: <GlobalOutlined /> },
    { path: '/ml', name: 'ML检测', icon: <ExperimentOutlined /> },
    { path: '/system', name: '系统监控', icon: <CloudServerOutlined /> },
    ...(isSuperAdmin ? [{ path: '/tenants', name: '租户管理', icon: <ApartmentOutlined /> }] : []),
    ...(isAdminRole ? [{ path: '/users', name: '用户管理', icon: <UserOutlined /> }] : []),
    { path: '/settings', name: '系统设置', icon: <SettingOutlined /> },
  ];

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
            <Button
              type="text"
              size="small"
              icon={<LogoutOutlined />}
              onClick={handleLogout}
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
        <Route path="/threat-intel" element={<ThreatIntel />} />
        <Route path="/ml" element={<MlDetection />} />
        <Route path="/system" element={<SystemMonitor />} />
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
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/*" element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        } />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
