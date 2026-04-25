import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, Space, message } from 'antd';
import { LockOutlined, UserOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '../../services/api';
import { useAuth, type AuthUser } from '@/contexts/AuthContext';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

export default function LoginPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleLogin = async (values: LoginForm) => {
    setLoading(true);
    try {
      const { data } = await apiClient.post('/api/v1/auth/login', values);
      if (data.token) {
        const user: AuthUser = {
          id: data.user?.id ?? 0,
          username: data.user?.username ?? values.username,
          displayName: data.user?.displayName,
          email: data.user?.email,
          roles: data.user?.roles ?? [],
          customerId: data.user?.customerId || undefined,
          tenantId: data.user?.tenantId || undefined,
        };
        login(data.token, data.refreshToken, user);
        message.success(t('login.welcome', { name: user.displayName || user.username }));
        navigate('/dashboard', { replace: true });
      } else {
        message.error(t('login.errorInvalidCredentials'));
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
              {t('login.title')}
            </Title>
            <Text type="secondary">{t('login.subtitle')}</Text>
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
              rules={[{ required: true, message: t('login.validationUsernameRequired') }]}
            >
              <Input prefix={<UserOutlined />} placeholder={t('login.username')} />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: t('login.validationPasswordRequired') }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder={t('login.password')} />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                {t('login.login')}
              </Button>
            </Form.Item>
          </Form>

          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('login.defaultAdmin')}
          </Text>
        </Space>
      </Card>
    </div>
  );
}
