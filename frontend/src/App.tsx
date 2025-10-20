import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import {
  DashboardOutlined,
  WarningOutlined,
  BarChartOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import Dashboard from './pages/Dashboard';
import ThreatList from './pages/ThreatList';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';

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
              path: '/analytics',
              name: '数据分析',
              icon: <BarChartOutlined />,
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
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </ProLayout>
    </BrowserRouter>
  );
}

export default App;
