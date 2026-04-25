import { useState, useRef, useCallback } from 'react';
import {
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Drawer,
  Table,
  Descriptions,
  Popconfirm,
  message,
  Card,
  Typography,
  Tabs,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  MobileOutlined,
  ReloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { Customer, Device, DeviceQuota } from '@/types';
import { CustomerStatus, SubscriptionTier } from '@/types';
import customerService from '@/services/customer';
import { getCustomerId } from '@/services/api';
import dayjs from 'dayjs';

const { Text } = Typography;

const statusColorMap: Record<string, string> = {
  [CustomerStatus.ACTIVE]: 'success',
  [CustomerStatus.INACTIVE]: 'default',
  [CustomerStatus.SUSPENDED]: 'error',
};

const tierColorMap: Record<string, string> = {
  [SubscriptionTier.BASIC]: 'default',
  [SubscriptionTier.STANDARD]: 'blue',
  [SubscriptionTier.PREMIUM]: 'gold',
  [SubscriptionTier.ENTERPRISE]: 'purple',
};

const CustomersTab = () => {
  const customerActionRef = useRef<ActionType>();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deviceDrawerOpen, setDeviceDrawerOpen] = useState(false);
  const [currentCustomer, setCurrentCustomer] = useState<Customer | null>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [deviceQuota, setDeviceQuota] = useState<DeviceQuota | null>(null);
  const [devicesLoading, setDevicesLoading] = useState(false);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [bindDeviceModalOpen, setBindDeviceModalOpen] = useState(false);
  const [editDeviceModalOpen, setEditDeviceModalOpen] = useState(false);
  const [currentDevice, setCurrentDevice] = useState<Device | null>(null);
  const [bindDeviceForm] = Form.useForm();
  const [editDeviceForm] = Form.useForm();

  const loadDevices = async (customerId: string) => {
    setDevicesLoading(true);
    try {
      const [devList, quota] = await Promise.all([
        customerService.getDevices(customerId),
        customerService.getDeviceQuota(customerId),
      ]);
      setDevices(devList);
      setDeviceQuota(quota);
    } catch {
      message.error('加载设备列表失败');
    } finally {
      setDevicesLoading(false);
    }
  };

  const handleCreate = async (values: Partial<Customer>) => {
    try {
      await customerService.createCustomer(values);
      message.success('客户创建成功');
      setCreateModalOpen(false);
      createForm.resetFields();
      customerActionRef.current?.reload();
    } catch {
      message.error('创建失败');
    }
  };

  const handleUpdate = async (values: Partial<Customer>) => {
    if (!currentCustomer) return;
    try {
      await customerService.updateCustomer(currentCustomer.customerId, values);
      message.success('客户更新成功');
      setEditModalOpen(false);
      customerActionRef.current?.reload();
    } catch {
      message.error('更新失败');
    }
  };

  const handleDelete = async (customerId: string) => {
    try {
      await customerService.deleteCustomer(customerId);
      message.success('删除成功');
      customerActionRef.current?.reload();
    } catch {
      message.error('删除失败');
    }
  };

  const handleDeleteDevice = async (devSerial: string) => {
    if (!currentCustomer) return;
    try {
      await customerService.deleteDevice(currentCustomer.customerId, devSerial);
      message.success('设备已删除');
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error('删除失败');
    }
  };

  const handleBindDevice = async () => {
    if (!currentCustomer) return;
    try {
      const values = await bindDeviceForm.validateFields();
      await customerService.addDevices(currentCustomer.customerId, [
        { devSerial: values.devSerial, description: values.description },
      ]);
      message.success('设备绑定成功');
      setBindDeviceModalOpen(false);
      bindDeviceForm.resetFields();
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error('设备绑定失败');
    }
  };

  const handleEditDevice = async () => {
    if (!currentCustomer || !currentDevice) return;
    try {
      const values = await editDeviceForm.validateFields();
      await customerService.updateDevice(
        currentCustomer.customerId,
        currentDevice.devSerial,
        { description: values.description, isActive: values.isActive },
      );
      message.success('设备更新成功');
      setEditDeviceModalOpen(false);
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error('更新失败');
    }
  };

  const handleSyncDevices = async () => {
    if (!currentCustomer) return;
    try {
      await customerService.syncDevices(currentCustomer.customerId);
      message.success('设备同步成功');
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error('同步失败');
    }
  };

  const columns: ProColumns<Customer>[] = [
    {
      title: '客户ID',
      dataIndex: 'customerId',
      copyable: true,
      width: 160,
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 150,
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 200,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: {
        ACTIVE: { text: '活跃', status: 'Success' },
        INACTIVE: { text: '未激活', status: 'Default' },
        SUSPENDED: { text: '已暂停', status: 'Error' },
      },
      render: (_, record) => (
        <Tag color={statusColorMap[record.status]}>{record.status}</Tag>
      ),
    },
    {
      title: '订阅等级',
      dataIndex: 'subscriptionTier',
      width: 120,
      search: false,
      render: (_, record) => (
        <Tag color={tierColorMap[record.subscriptionTier]}>
          {record.subscriptionTier}
        </Tag>
      ),
    },
    {
      title: '防护主机',
      width: 120,
      search: false,
      render: (_, record) => (
        <span>
          {record.currentDevices}/{record.maxDevices}
        </span>
      ),
    },
    {
      title: '告警',
      dataIndex: 'alertEnabled',
      width: 80,
      search: false,
      render: (val) => (val ? <Tag color="green">开启</Tag> : <Tag>关闭</Tag>),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      search: false,
      render: (_, record) =>
        record.createdAt
          ? dayjs(
              typeof record.createdAt === 'number'
                ? record.createdAt * 1000
                : record.createdAt,
            ).format('YYYY-MM-DD HH:mm')
          : '-',
    },
    {
      title: '操作',
      width: 200,
      key: 'actions',
      search: false,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setCurrentCustomer(record);
              editForm.setFieldsValue(record);
              setEditModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<MobileOutlined />}
            onClick={() => {
              setCurrentCustomer(record);
              setDeviceDrawerOpen(true);
              loadDevices(record.customerId);
            }}
          >
            设备
          </Button>
          <Popconfirm
            title="确定删除该客户?"
            onConfirm={() => handleDelete(record.customerId)}
          >
            <Button
              type="link"
              size="small"
              danger
              aria-label={`删除客户 ${record.customerId}`}
              icon={<DeleteOutlined />}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const deviceColumns = [
    { title: '序列号', dataIndex: 'devSerial', key: 'devSerial' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    {
      title: '发现主机数',
      dataIndex: 'realHostCount',
      key: 'realHostCount',
      render: (val: number | null | undefined) => (val != null ? val : '-'),
    },
    {
      title: '状态',
      dataIndex: 'isActive',
      key: 'isActive',
      render: (val: boolean) => (
        <Tag color={val ? 'green' : 'default'}>{val ? '激活' : '未激活'}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val: string | number) =>
        val
          ? dayjs(typeof val === 'number' ? val * 1000 : val).format(
              'YYYY-MM-DD HH:mm',
            )
          : '-',
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: Device) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setCurrentDevice(record);
              editDeviceForm.setFieldsValue({
                description: record.description,
                isActive: record.isActive !== false,
              });
              setEditDeviceModalOpen(true);
            }}
          />
          <Popconfirm
            title="确定解绑?"
            onConfirm={() => handleDeleteDevice(record.devSerial)}
          >
            <Button
              type="link"
              size="small"
              danger
              aria-label={`删除设备 ${record.devSerial}`}
              icon={<DeleteOutlined />}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const customerFormFields = (
    <>
      <Form.Item name="customerId" label="客户ID" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item name="name" label="名称" rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item
        name="email"
        label="邮箱"
        rules={[{ required: true, type: 'email' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item name="phone" label="电话">
        <Input />
      </Form.Item>
      <Form.Item name="address" label="地址">
        <Input />
      </Form.Item>
      <Form.Item name="status" label="状态" initialValue="ACTIVE">
        <Select
          options={[
            { label: '活跃', value: 'ACTIVE' },
            { label: '未激活', value: 'INACTIVE' },
            { label: '已暂停', value: 'SUSPENDED' },
          ]}
        />
      </Form.Item>
      <Form.Item name="subscriptionTier" label="订阅等级" initialValue="BASIC">
        <Select
          options={[
            { label: 'Basic', value: 'BASIC' },
            { label: 'Standard', value: 'STANDARD' },
            { label: 'Premium', value: 'PREMIUM' },
            { label: 'Enterprise', value: 'ENTERPRISE' },
          ]}
        />
      </Form.Item>
      <Form.Item
        name="maxDevices"
        label="最大防护设备数 (授权在线设备数)"
        initialValue={10}
      >
        <Input type="number" />
      </Form.Item>
      <Form.Item name="description" label="描述">
        <Input.TextArea rows={2} />
      </Form.Item>
    </>
  );

  return (
    <>
      <ProTable<Customer>
        headerTitle="客户管理"
        actionRef={customerActionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          try {
            const result = await customerService.getCustomers({
              page: (params.current || 1) - 1,
              size: params.pageSize || 20,
            });
            return {
              data: result.content,
              total: result.totalElements,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ defaultPageSize: 20 }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              createForm.resetFields();
              setCreateModalOpen(true);
            }}
          >
            新建客户
          </Button>,
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => customerActionRef.current?.reload()}
          >
            刷新
          </Button>,
        ]}
      />

      <Modal
        title="新建客户"
        open={createModalOpen}
        onOk={() => createForm.submit()}
        onCancel={() => setCreateModalOpen(false)}
        width={600}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          {customerFormFields}
        </Form>
      </Modal>

      <Modal
        title="编辑客户"
        open={editModalOpen}
        onOk={() => editForm.submit()}
        onCancel={() => setEditModalOpen(false)}
        width={600}
      >
        <Form form={editForm} layout="vertical" onFinish={handleUpdate}>
          {customerFormFields}
        </Form>
      </Modal>

      <Drawer
        title={`设备管理 - ${currentCustomer?.name || ''}`}
        open={deviceDrawerOpen}
        onClose={() => setDeviceDrawerOpen(false)}
        width={800}
        extra={
          <Space>
            <Button
              size="small"
              icon={<SyncOutlined />}
              onClick={handleSyncDevices}
            >
              同步
            </Button>
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => {
                bindDeviceForm.resetFields();
                setBindDeviceModalOpen(true);
              }}
            >
              绑定设备
            </Button>
          </Space>
        }
      >
        {deviceQuota && (
          <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="客户ID">{deviceQuota.customerId}</Descriptions.Item>
            <Descriptions.Item label="最大防护设备数">
              {deviceQuota.maxDevices}
            </Descriptions.Item>
            <Descriptions.Item label="当前防护主机数">
              {deviceQuota.protectedHostCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="终端设备数">
              {deviceQuota.currentDevices}
            </Descriptions.Item>
            <Descriptions.Item label="剩余配额">
              {deviceQuota.remainingQuota}
            </Descriptions.Item>
          </Descriptions>
        )}
        <Table
          columns={deviceColumns}
          dataSource={devices}
          rowKey="devSerial"
          loading={devicesLoading}
          size="small"
          pagination={false}
        />
      </Drawer>

      <Modal
        title="绑定新设备"
        open={bindDeviceModalOpen}
        onOk={handleBindDevice}
        onCancel={() => {
          setBindDeviceModalOpen(false);
          bindDeviceForm.resetFields();
        }}
        okText="绑定"
        cancelText="取消"
      >
        <Form form={bindDeviceForm} layout="vertical">
          <Form.Item
            name="devSerial"
            label="设备序列号"
            rules={[{ required: true, message: '请输入设备序列号' }]}
          >
            <Input placeholder="例如: JZ-SNIFF-001" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="设备描述（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑设备"
        open={editDeviceModalOpen}
        onOk={handleEditDevice}
        onCancel={() => setEditDeviceModalOpen(false)}
        okText="保存"
        cancelText="取消"
      >
        <Form form={editDeviceForm} layout="vertical">
          <Form.Item label="设备序列号">
            <Input value={currentDevice?.devSerial} disabled />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="isActive" label="状态">
            <Select
              options={[
                { label: '激活', value: true },
                { label: '停用', value: false },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const DevicesTab = () => {
  const deviceActionRef = useRef<ActionType>();
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
      const customers: Customer[] = await customerService.searchCustomers(keyword);
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
    setTimeout(() => deviceActionRef.current?.reload(), 0);
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
      deviceActionRef.current?.reload();
    } catch {
      message.error('设备绑定失败');
    }
  };

  const handleUnbind = async (devSerial: string) => {
    if (!customerId) return;
    try {
      await customerService.deleteDevice(customerId, devSerial);
      message.success('设备已解绑');
      deviceActionRef.current?.reload();
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
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm') : '-',
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
          {customerId && <Text type="secondary">当前客户: {customerId}</Text>}
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
            headerTitle="设备列表"
            actionRef={deviceActionRef}
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
                onClick={() => deviceActionRef.current?.reload()}
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

const CustomersAndDevices = () => {
  return (
    <Tabs
      items={[
        {
          key: 'customers',
          label: '客户管理',
          children: <CustomersTab />,
        },
        {
          key: 'devices',
          label: '设备管理',
          children: <DevicesTab />,
        },
      ]}
    />
  );
};

export default CustomersAndDevices;
