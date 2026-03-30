import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, Space, message } from 'antd';
import { LockOutlined, UserOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import apiClient from '../../services/api';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const { data } = await apiClient.post('/api/v1/auth/login', values);
      if (data.token) {
        localStorage.setItem('token', data.token);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('user', JSON.stringify(data.user));
        if (data.user?.customerId) {
          localStorage.setItem('customer_id', data.user.customerId);
        }
        if (data.user?.tenantId) {
          localStorage.setItem('tenant_id', String(data.user.tenantId));
        }
        message.success(`欢迎, ${data.user?.displayName || data.user?.username}`);
        navigate('/dashboard', { replace: true });
      } else {
        message.error('登录失败：用户名或密码错误');
      }
    } catch {
      // error interceptor in api.ts already shows message
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0a1628 0%, #1a2a4a 50%, #0d1f3c 100%)',
    }}>
      <Card
        style={{
          width: 420,
          borderRadius: 8,
          boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
        }}
        bordered={false}
      >
        <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
          <div>
            <SafetyCertificateOutlined style={{ fontSize: 48, color: '#1677ff' }} />
            <Title level={3} style={{ marginTop: 12, marginBottom: 0 }}>
              威胁检测系统
            </Title>
            <Text type="secondary">Cloud-Native Threat Detection Platform</Text>
          </div>

          <Form<LoginForm>
            name="login"
            onFinish={handleLogin}
            autoComplete="off"
            layout="vertical"
            size="large"
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input prefix={<UserOutlined />} placeholder="用户名" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                登录
              </Button>
            </Form.Item>
          </Form>

          <Text type="secondary" style={{ fontSize: 12 }}>
            默认管理员: admin / admin123
          </Text>
        </Space>
      </Card>
    </div>
  );
}
