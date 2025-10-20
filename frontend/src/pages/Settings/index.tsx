import { Card, Form, Input, Button, Switch, message } from 'antd';

/**
 * 系统设置页面 (待实现)
 */
const Settings = () => {
  const [form] = Form.useForm();

  const handleSave = (values: any) => {
    console.log('Settings saved:', values);
    message.success('设置已保存');
  };

  return (
    <Card title="系统设置" bordered={false}>
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSave}
        initialValues={{
          customer_id: localStorage.getItem('customer_id') || 'demo-customer',
          auto_refresh: true,
          refresh_interval: 30,
        }}
      >
        <Form.Item
          label="客户ID"
          name="customer_id"
          rules={[{ required: true, message: '请输入客户ID' }]}
        >
          <Input placeholder="demo-customer" />
        </Form.Item>

        <Form.Item
          label="自动刷新"
          name="auto_refresh"
          valuePropName="checked"
        >
          <Switch />
        </Form.Item>

        <Form.Item
          label="刷新间隔 (秒)"
          name="refresh_interval"
          rules={[{ required: true, message: '请输入刷新间隔' }]}
        >
          <Input type="number" min={10} max={300} />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit">
            保存设置
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default Settings;
