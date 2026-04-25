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
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      message.error(t('customersDevices.loadDevicesFailed'));
    } finally {
      setDevicesLoading(false);
    }
  };

  const handleCreate = async (values: Partial<Customer>) => {
    try {
      await customerService.createCustomer(values);
      message.success(t('customersDevices.customerCreated'));
      setCreateModalOpen(false);
      createForm.resetFields();
      customerActionRef.current?.reload();
    } catch {
      message.error(t('common.createFailed'));
    }
  };

  const handleUpdate = async (values: Partial<Customer>) => {
    if (!currentCustomer) return;
    try {
      await customerService.updateCustomer(currentCustomer.customerId, values);
      message.success(t('customersDevices.customerUpdated'));
      setEditModalOpen(false);
      customerActionRef.current?.reload();
    } catch {
      message.error(t('common.updateFailed'));
    }
  };

  const handleDelete = async (customerId: string) => {
    try {
      await customerService.deleteCustomer(customerId);
      message.success(t('common.deleteSuccess'));
      customerActionRef.current?.reload();
    } catch {
      message.error(t('common.deleteFailed'));
    }
  };

  const handleDeleteDevice = async (devSerial: string) => {
    if (!currentCustomer) return;
    try {
      await customerService.deleteDevice(currentCustomer.customerId, devSerial);
      message.success(t('customersDevices.deviceDeleted'));
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t('common.deleteFailed'));
    }
  };

  const handleBindDevice = async () => {
    if (!currentCustomer) return;
    try {
      const values = await bindDeviceForm.validateFields();
      await customerService.addDevices(currentCustomer.customerId, [
        { devSerial: values.devSerial, description: values.description },
      ]);
      message.success(t('customersDevices.deviceBound'));
      setBindDeviceModalOpen(false);
      bindDeviceForm.resetFields();
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t('customersDevices.bindDeviceFailed'));
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
      message.success(t('customersDevices.deviceUpdated'));
      setEditDeviceModalOpen(false);
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t('common.updateFailed'));
    }
  };

  const handleSyncDevices = async () => {
    if (!currentCustomer) return;
    try {
      await customerService.syncDevices(currentCustomer.customerId);
      message.success(t('customersDevices.deviceSynced'));
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t('customersDevices.syncFailed'));
    }
  };

  const columns: ProColumns<Customer>[] = [
    {
      title: t('common.customerId'),
      dataIndex: 'customerId',
      copyable: true,
      width: 160,
    },
    {
      title: t('common.name'),
      dataIndex: 'name',
      width: 150,
    },
    {
      title: t('common.email'),
      dataIndex: 'email',
      width: 200,
      search: false,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: {
          ACTIVE: { text: t('customersDevices.active'), status: 'Success' },
          INACTIVE: { text: t('customersDevices.inactive'), status: 'Default' },
          SUSPENDED: { text: t('customersDevices.suspended'), status: 'Error' },
      },
      render: (_, record) => (
        <Tag color={statusColorMap[record.status]}>{record.status}</Tag>
      ),
    },
    {
      title: t('customersDevices.subscriptionTier'),
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
      title: t('customersDevices.protectedHosts'),
      width: 120,
      search: false,
      render: (_, record) => (
        <span>
          {record.currentDevices}/{record.maxDevices}
        </span>
      ),
    },
    {
      title: t('customersDevices.alerts'),
      dataIndex: 'alertEnabled',
      width: 80,
      search: false,
      render: (val) => (val ? <Tag color="green">{t('common.enabled')}</Tag> : <Tag>{t('common.disabled')}</Tag>),
    },
    {
      title: t('common.createdAt'),
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
      title: t('common.actions'),
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
            {t('common.edit')}
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
            {t('customersDevices.devices')}
          </Button>
          <Popconfirm
            title={t('customersDevices.confirmDeleteCustomer')}
            onConfirm={() => handleDelete(record.customerId)}
          >
            <Button
              type="link"
              size="small"
              danger
              aria-label={t('customersDevices.deleteCustomerAria', { customerId: record.customerId })}
              icon={<DeleteOutlined />}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const deviceColumns = [
    { title: t('customersDevices.serialNumber'), dataIndex: 'devSerial', key: 'devSerial' },
    { title: t('common.description'), dataIndex: 'description', key: 'description' },
    {
      title: t('customersDevices.discoveredHosts'),
      dataIndex: 'realHostCount',
      key: 'realHostCount',
      render: (val: number | null | undefined) => (val != null ? val : '-'),
    },
    {
      title: t('common.status'),
      dataIndex: 'isActive',
      key: 'isActive',
      render: (val: boolean) => (
        <Tag color={val ? 'green' : 'default'}>{val ? t('customersDevices.active') : t('customersDevices.inactive')}</Tag>
      ),
    },
    {
      title: t('common.createdAt'),
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
      title: t('common.actions'),
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
            title={t('customersDevices.confirmUnbind')}
            onConfirm={() => handleDeleteDevice(record.devSerial)}
          >
            <Button
              type="link"
              size="small"
              danger
              aria-label={t('customersDevices.deleteDeviceAria', { devSerial: record.devSerial })}
              icon={<DeleteOutlined />}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const customerFormFields = (
    <>
      <Form.Item name="customerId" label={t('common.customerId')} rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item name="name" label={t('common.name')} rules={[{ required: true }]}>
        <Input />
      </Form.Item>
      <Form.Item
        name="email"
        label={t('common.email')}
        rules={[{ required: true, type: 'email' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item name="phone" label={t('common.phone')}>
        <Input />
      </Form.Item>
      <Form.Item name="address" label={t('common.address')}>
        <Input />
      </Form.Item>
      <Form.Item name="status" label={t('common.status')} initialValue="ACTIVE">
        <Select
          options={[
            { label: t('customersDevices.active'), value: 'ACTIVE' },
            { label: t('customersDevices.inactive'), value: 'INACTIVE' },
            { label: t('customersDevices.suspended'), value: 'SUSPENDED' },
          ]}
        />
      </Form.Item>
      <Form.Item name="subscriptionTier" label={t('customersDevices.subscriptionTier')} initialValue="BASIC">
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
        label={t('customersDevices.maxProtectedDevices')}
        initialValue={10}
      >
        <Input type="number" />
      </Form.Item>
      <Form.Item name="description" label={t('common.description')}>
        <Input.TextArea rows={2} />
      </Form.Item>
    </>
  );

  return (
    <>
      <ProTable<Customer>
        headerTitle={t('customersDevices.customerManagement')}
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
            {t('customersDevices.createCustomer')}
          </Button>,
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => customerActionRef.current?.reload()}
          >
            {t('common.refresh')}
          </Button>,
        ]}
      />

      <Modal
        title={t('customersDevices.createCustomer')}
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
        title={t('customersDevices.editCustomer')}
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
        title={t('customersDevices.deviceManagementTitle', { name: currentCustomer?.name || '' })}
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
              {t('customersDevices.sync')}
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
              {t('customersDevices.bindDevice')}
            </Button>
          </Space>
        }
      >
        {deviceQuota && (
          <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label={t('common.customerId')}>{deviceQuota.customerId}</Descriptions.Item>
            <Descriptions.Item label={t('customersDevices.maxProtectedDevicesShort')}>
              {deviceQuota.maxDevices}
            </Descriptions.Item>
            <Descriptions.Item label={t('customersDevices.currentProtectedHostCount')}>
              {deviceQuota.protectedHostCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label={t('customersDevices.deviceCount')}>
              {deviceQuota.currentDevices}
            </Descriptions.Item>
            <Descriptions.Item label={t('customersDevices.remainingQuota')}>
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
        title={t('customersDevices.bindNewDevice')}
        open={bindDeviceModalOpen}
        onOk={handleBindDevice}
        onCancel={() => {
          setBindDeviceModalOpen(false);
          bindDeviceForm.resetFields();
        }}
        okText={t('customersDevices.bind')}
        cancelText={t('common.cancel')}
      >
        <Form form={bindDeviceForm} layout="vertical">
          <Form.Item
            name="devSerial"
            label={t('customersDevices.deviceSerial')}
            rules={[{ required: true, message: t('customersDevices.validationDeviceSerialRequired') }]}
          >
            <Input placeholder={t('customersDevices.deviceSerialExample')} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} placeholder={t('customersDevices.deviceDescriptionOptional')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('customersDevices.editDevice')}
        open={editDeviceModalOpen}
        onOk={handleEditDevice}
        onCancel={() => setEditDeviceModalOpen(false)}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={editDeviceForm} layout="vertical">
          <Form.Item label={t('customersDevices.deviceSerial')}>
            <Input value={currentDevice?.devSerial} disabled />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="isActive" label={t('common.status')}>
            <Select
              options={[
                { label: t('customersDevices.active'), value: true },
                { label: t('customersDevices.deactivated'), value: false },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const DevicesTab = () => {
  const { t } = useTranslation();
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
      message.success(t('customersDevices.deviceBound'));
      setBindModalOpen(false);
      bindForm.resetFields();
      deviceActionRef.current?.reload();
    } catch {
      message.error(t('customersDevices.bindDeviceFailed'));
    }
  };

  const handleUnbind = async (devSerial: string) => {
    if (!customerId) return;
    try {
      await customerService.deleteDevice(customerId, devSerial);
      message.success(t('customersDevices.deviceUnbound'));
      deviceActionRef.current?.reload();
    } catch {
      message.error(t('customersDevices.unbindFailed'));
    }
  };

  const columns: ProColumns<Device>[] = [
    {
      title: t('customersDevices.deviceSerial'),
      dataIndex: 'devSerial',
      copyable: true,
      ellipsis: true,
    },
    {
      title: t('common.status'),
      dataIndex: 'isActive',
      valueType: 'select',
      valueEnum: {
          true: { text: t('customersDevices.active'), status: 'Success' },
          false: { text: t('customersDevices.deactivated'), status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={record.isActive !== false ? 'success' : 'default'}>
          {record.isActive !== false ? t('customersDevices.active') : t('customersDevices.deactivated')}
        </Tag>
      ),
    },
    {
      title: t('common.description'),
      dataIndex: 'description',
      ellipsis: true,
      search: false,
    },
    {
      title: t('customersDevices.protectedHostCount'),
      dataIndex: 'realHostCount',
      search: false,
      sorter: true,
    },
    {
      title: t('customersDevices.boundAt'),
      dataIndex: 'createdAt',
      valueType: 'dateTime',
      search: false,
      sorter: true,
      render: (_, record) =>
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'),
      valueType: 'option',
      render: (_, record) => (
        <Space>
          <Popconfirm
            title={t('customersDevices.confirmUnbindDevice')}
            onConfirm={() => handleUnbind(record.devSerial)}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              {t('customersDevices.unbind')}
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
          <Text strong>{t('customersDevices.selectCustomer')}：</Text>
          <Select
            showSearch
            allowClear
            placeholder={t('customersDevices.searchCustomerPlaceholder')}
            value={customerId}
            onChange={handleCustomerChange}
            onSearch={handleCustomerSearch}
            loading={customerLoading}
            options={customerOptions}
            filterOption={false}
            style={{ minWidth: 320 }}
            notFoundContent={customerLoading ? t('customersDevices.searching') : t('customersDevices.noMatchingCustomer')}
          />
          {customerId && <Text type="secondary">{t('customersDevices.currentCustomer')}: {customerId}</Text>}
        </Space>
      </Card>

      {!customerId ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '48px 0', color: '#999' }}>
            {t('customersDevices.selectCustomerFirst')}
          </div>
        </Card>
      ) : (
        <>
          <ProTable<Device>
            headerTitle={t('customersDevices.deviceList')}
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
                message.error(t('customersDevices.loadDevicesFailed'));
                return { data: [], success: false, total: 0 };
              }
            }}
            toolBarRender={() => [
              <Button
                key="reload"
                icon={<ReloadOutlined />}
                onClick={() => deviceActionRef.current?.reload()}
              >
                {t('common.refresh')}
              </Button>,
              <Button
                key="bind"
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setBindModalOpen(true)}
              >
                {t('customersDevices.bindDevice')}
              </Button>,
            ]}
            pagination={{ defaultPageSize: 20 }}
          />

          <Modal
            title={t('customersDevices.bindNewDevice')}
            open={bindModalOpen}
            onOk={handleBind}
            onCancel={() => {
              setBindModalOpen(false);
              bindForm.resetFields();
            }}
            okText={t('customersDevices.bind')}
            cancelText={t('common.cancel')}
          >
            <Form form={bindForm} layout="vertical">
              <Form.Item
                name="devSerial"
                label={t('customersDevices.deviceSerial')}
                rules={[{ required: true, message: t('customersDevices.validationDeviceSerialRequired') }]}
              >
                <Input placeholder={t('customersDevices.deviceSerialExample')} />
              </Form.Item>
              <Form.Item name="description" label={t('common.description')}>
                <Input.TextArea rows={3} placeholder={t('customersDevices.deviceDescriptionOptional')} />
              </Form.Item>
            </Form>
          </Modal>
        </>
      )}
    </div>
  );
};

const CustomersAndDevices = () => {
  const { t } = useTranslation();
  return (
    <Tabs
      items={[
        {
          key: 'customers',
          label: t('customersDevices.customerManagement'),
          children: <CustomersTab />,
        },
        {
          key: 'devices',
          label: t('customersDevices.deviceManagement'),
          children: <DevicesTab />,
        },
      ]}
    />
  );
};

export default CustomersAndDevices;
