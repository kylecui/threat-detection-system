import React from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, Drawer } from 'antd';
import { AuthProvider } from '../../src/contexts/AuthContext';
import DeviceControlPanel from '../../src/components/DeviceControlPanel';
import '../../src/index.css';

// Fixture identity only; Playwright intercepts every network API in this harness.
localStorage.setItem('user', JSON.stringify({ id: 18, username: 'fixture-admin', roles: ['CUSTOMER_ADMIN'], customerId: 'fixture-customer', tenantId: 8 }));
createRoot(document.getElementById('root')!).render(<React.StrictMode><ConfigProvider><AuthProvider>
  <Drawer title="设备控制 · FIXTURE-SERIAL" open width="min(960px, 100vw)" onClose={() => { }}>
    <DeviceControlPanel customerId="fixture-customer" devSerial="FIXTURE-SERIAL" />
  </Drawer>
</AuthProvider></ConfigProvider></React.StrictMode>);
