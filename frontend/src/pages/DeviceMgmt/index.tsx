import { useState, useRef } from 'react';
import {
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Popconfirm,
  message,
  Alert,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { Device } from '@/types';
import customerService from '@/services/customer';
import { getCustomerId } from '@/services/api';
import dayjs from 'dayjs';

const DeviceMgmt = () => {
  const actionRef = useRef<ActionType>();
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindForm] = Form.useForm();

  const customerId = getCustomerId();

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
      title: '客户ID',
      dataIndex: 'customerId',
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

  if (!customerId) {
    return (
      <Alert
        type="warning"
        showIcon
        message="未选择客户"
        description="请先在系统设置页面选择一个客户，或使用具有客户绑定的账号登录。"
        style={{ margin: 24 }}
      />
    );
  }

  return (
    <>
      <ProTable<Device>
        headerTitle="设备管理"
        actionRef={actionRef}
        rowKey="devSerial"
        columns={columns}
        search={{ labelWidth: 'auto' }}
        request={async () => {
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
  );
};

export default DeviceMgmt;
