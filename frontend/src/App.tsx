import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
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

function App() {
  return (
    <BrowserRouter>
      <ProLayout
        title="威胁检测系统"
        logo={<DashboardOutlined />}
        layout="mix"
        fixedHeader
        fixSiderbar
        route={{
          path: '/',
          routes: [
            {
              path: '/dashboard',
              name: '仪表盘',
              icon: <DashboardOutlined />,
            },
            {
              path: '/threats',
              name: '威胁列表',
              icon: <WarningOutlined />,
            },
            {
              path: '/alerts',
              name: '告警中心',
              icon: <BellOutlined />,
            },
            {
              path: '/analytics',
              name: '数据分析',
              icon: <BarChartOutlined />,
            },
            {
              path: '/customers',
              name: '客户管理',
              icon: <TeamOutlined />,
            },
            {
              path: '/threat-intel',
              name: '威胁情报',
              icon: <GlobalOutlined />,
            },
            {
              path: '/ml',
              name: 'ML检测',
              icon: <ExperimentOutlined />,
            },
            {
              path: '/system',
              name: '系统监控',
              icon: <CloudServerOutlined />,
            },
            {
              path: '/settings',
              name: '系统设置',
              icon: <SettingOutlined />,
            },
          ],
        }}
        menuItemRender={(item, dom) => (
          <a
            onClick={() => {
              window.location.href = item.path || '/';
            }}
          >
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
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </ProLayout>
    </BrowserRouter>
  );
}

export default App;
