import { Card, Form, Input, InputNumber, Button, Switch, message, Alert, Divider, Descriptions, Tag } from 'antd';
import { UserOutlined, SaveOutlined } from '@ant-design/icons';
import { getCustomerId } from '@/services/api';

const GeneralConfig = () => {
  const [basicForm] = Form.useForm();
  const customerId = getCustomerId();

  const handleBasicSave = (values: {
    customer_id: string;
    auto_refresh: boolean;
    refresh_interval: number;
  }) => {
    localStorage.setItem('customer_id', values.customer_id);
    localStorage.setItem('auto_refresh', String(values.auto_refresh));
    localStorage.setItem('refresh_interval', String(values.refresh_interval));
    message.success('基础设置已保存');
  };

  return (
    <Card bordered={false}>
      <Alert
        message="客户ID用于多租户数据隔离，修改后所有API请求将使用新的客户ID"
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />
      <Form
        form={basicForm}
        layout="vertical"
        onFinish={handleBasicSave}
        initialValues={{
          customer_id: customerId,
          auto_refresh: localStorage.getItem('auto_refresh') !== 'false',
          refresh_interval: Number(localStorage.getItem('refresh_interval')) || 30,
        }}
        style={{ maxWidth: 500 }}
      >
        <Form.Item
          label="客户ID (Customer ID)"
          name="customer_id"
          rules={[{ required: true, message: '请输入客户ID' }]}
          tooltip="多租户隔离标识，用于API请求"
        >
          <Input prefix={<UserOutlined />} placeholder="demo-customer" />
        </Form.Item>

        <Form.Item
          label="自动刷新"
          name="auto_refresh"
          valuePropName="checked"
          tooltip="启用后仪表盘和监控页面将自动刷新数据"
        >
          <Switch checkedChildren="开" unCheckedChildren="关" />
        </Form.Item>

        <Form.Item
          label="刷新间隔 (秒)"
          name="refresh_interval"
          rules={[{ required: true, message: '请输入刷新间隔' }]}
          tooltip="自动刷新的时间间隔，建议10-300秒"
        >
          <InputNumber min={10} max={300} step={5} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
            保存基础设置
          </Button>
        </Form.Item>
      </Form>

      <Divider />
      <Descriptions title="当前环境信息" bordered column={1} size="small">
        <Descriptions.Item label="API基础地址">
          {import.meta.env.VITE_API_BASE_URL || '/api'}
        </Descriptions.Item>
        <Descriptions.Item label="当前客户ID">
          <Tag color="blue">{customerId}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="应用版本">v1.0.0</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default GeneralConfig;
