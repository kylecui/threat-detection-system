import { useState, useRef, useCallback } from 'react';
import {
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Popconfirm,
  message,
  Select,
  Card,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { Device, Customer } from '@/types';
import customerService from '@/services/customer';
import { getCustomerId } from '@/services/api';
import dayjs from 'dayjs';

const { Text } = Typography;

const DeviceMgmt = () => {
  const actionRef = useRef<ActionType>();
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindForm] = Form.useForm();

  const storedCustomerId = getCustomerId();
  const [customerId, setCustomerId] = useState<string | undefined>(
    storedCustomerId || undefined,
  );
  const [customerOptions, setCustomerOptions] = useState<
    { label: string; value: string }[]
  >([]);
  const [customerLoading, setCustomerLoading] = useState(false);

  const handleCustomerSearch = useCallback(async (keyword: string) => {
    if (!keyword || keyword.length < 1) {
      setCustomerOptions([]);
      return;
    }
    setCustomerLoading(true);
    try {
      const customers: Customer[] =
        await customerService.searchCustomers(keyword);
      setCustomerOptions(
        customers.map((c) => ({
          label: `${c.name} (${c.customerId})`,
          value: c.customerId,
        })),
      );
    } catch {
      setCustomerOptions([]);
    } finally {
      setCustomerLoading(false);
    }
  }, []);

  const handleCustomerChange = (value: string) => {
    setCustomerId(value);
    if (value) {
      localStorage.setItem('customer_id', value);
    }
    setTimeout(() => actionRef.current?.reload(), 0);
  };

  const handleBind = async () => {
    try {
      const values = await bindForm.validateFields();
      if (!customerId) return;
      await customerService.addDevices(customerId, [
        { devSerial: values.devSerial, description: values.description },
      ]);
      message.success('设备绑定成功');
      setBindModalOpen(false);
      bindForm.resetFields();
      actionRef.current?.reload();
    } catch {
      message.error('设备绑定失败');
    }
  };

  const handleUnbind = async (devSerial: string) => {
    if (!customerId) return;
    try {
      await customerService.deleteDevice(customerId, devSerial);
      message.success('设备已解绑');
      actionRef.current?.reload();
    } catch {
      message.error('解绑失败');
    }
  };

  const columns: ProColumns<Device>[] = [
    {
      title: '设备序列号',
      dataIndex: 'devSerial',
      copyable: true,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'isActive',
      valueType: 'select',
      valueEnum: {
        true: { text: '活跃', status: 'Success' },
        false: { text: '停用', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={record.isActive !== false ? 'success' : 'default'}>
          {record.isActive !== false ? '活跃' : '停用'}
        </Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      search: false,
    },
    {
      title: '受保护主机数',
      dataIndex: 'realHostCount',
      search: false,
      sorter: true,
    },
    {
      title: '绑定时间',
      dataIndex: 'createdAt',
      valueType: 'dateTime',
      search: false,
      sorter: true,
      render: (_, record) =>
        record.createdAt
          ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm')
          : '-',
    },
    {
      title: '操作',
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <Popconfirm
            title="确认解绑此设备?"
            onConfirm={() => handleUnbind(record.devSerial)}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              解绑
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card size="small">
        <Space align="center" wrap>
          <Text strong>选择客户：</Text>
          <Select
            showSearch
            allowClear
            placeholder="输入客户名称或ID搜索"
            value={customerId}
            onChange={handleCustomerChange}
            onSearch={handleCustomerSearch}
            loading={customerLoading}
            options={customerOptions}
            filterOption={false}
            style={{ minWidth: 320 }}
            notFoundContent={customerLoading ? '搜索中...' : '无匹配客户'}
          />
          {customerId && (
            <Text type="secondary">当前客户: {customerId}</Text>
          )}
        </Space>
      </Card>

      {!customerId ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '48px 0', color: '#999' }}>
            请先在上方选择一个客户，然后管理其设备。
          </div>
        </Card>
      ) : (
        <>
          <ProTable<Device>
            headerTitle={`设备列表`}
            actionRef={actionRef}
            rowKey="devSerial"
            columns={columns}
            search={{ labelWidth: 'auto' }}
            request={async () => {
              if (!customerId) return { data: [], success: true, total: 0 };
              try {
                const data = await customerService.getDevices(customerId);
                return { data, success: true, total: data.length };
              } catch {
                message.error('加载设备列表失败');
                return { data: [], success: false, total: 0 };
              }
            }}
            toolBarRender={() => [
              <Button
                key="reload"
                icon={<ReloadOutlined />}
                onClick={() => actionRef.current?.reload()}
              >
                刷新
              </Button>,
              <Button
                key="bind"
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setBindModalOpen(true)}
              >
                绑定设备
              </Button>,
            ]}
            pagination={{ defaultPageSize: 20 }}
          />

          <Modal
            title="绑定新设备"
            open={bindModalOpen}
            onOk={handleBind}
            onCancel={() => {
              setBindModalOpen(false);
              bindForm.resetFields();
            }}
            okText="绑定"
            cancelText="取消"
          >
            <Form form={bindForm} layout="vertical">
              <Form.Item
                name="devSerial"
                label="设备序列号"
                rules={[{ required: true, message: '请输入设备序列号' }]}
              >
                <Input placeholder="例如: JZ-SNIFF-001" />
              </Form.Item>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={3} placeholder="设备描述（可选）" />
              </Form.Item>
            </Form>
          </Modal>
        </>
      )}
    </div>
  );
};

export default DeviceMgmt;
