import { useState, useRef } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Tag,
  Button,
  Space,
  Modal,
  Input,
  message,
  Tooltip,
} from 'antd';
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  UserOutlined,
  ArrowUpOutlined,
  StopOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { Alert, AlertAnalytics } from '@/types';
import { AlertStatus, AlertSeverity } from '@/types';
import alertService from '@/services/alert';
import dayjs from 'dayjs';
import { useEffect } from 'react';

/** 严重程度颜色 */
const severityColorMap: Record<string, string> = {
  [AlertSeverity.CRITICAL]: 'red',
  [AlertSeverity.HIGH]: 'orange',
  [AlertSeverity.MEDIUM]: 'gold',
  [AlertSeverity.LOW]: 'blue',
  [AlertSeverity.INFO]: 'default',
};

/** 状态颜色 */
const statusColorMap: Record<string, string> = {
  [AlertStatus.NEW]: 'processing',
  [AlertStatus.DEDUPLICATED]: 'cyan',
  [AlertStatus.ENRICHED]: 'geekblue',
  [AlertStatus.NOTIFIED]: 'purple',
  [AlertStatus.ESCALATED]: 'volcano',
  [AlertStatus.RESOLVED]: 'success',
  [AlertStatus.ARCHIVED]: 'default',
};

/** 状态中文名 */
const statusLabelMap: Record<string, string> = {
  [AlertStatus.NEW]: '新建',
  [AlertStatus.DEDUPLICATED]: '去重',
  [AlertStatus.ENRICHED]: '已丰富',
  [AlertStatus.NOTIFIED]: '已通知',
  [AlertStatus.ESCALATED]: '已升级',
  [AlertStatus.RESOLVED]: '已解决',
  [AlertStatus.ARCHIVED]: '已归档',
};

/**
 * 告警中心页面
 */
const AlertCenter = () => {
  const actionRef = useRef<ActionType>();
  const [analytics, setAnalytics] = useState<AlertAnalytics | null>(null);
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [escalateModalOpen, setEscalateModalOpen] = useState(false);
  const [currentAlert, setCurrentAlert] = useState<Alert | null>(null);
  const [resolveForm, setResolveForm] = useState({ resolution: '', resolvedBy: '' });
  const [assignTo, setAssignTo] = useState('');
  const [escalateReason, setEscalateReason] = useState('');

  /** 加载分析数据 */
  useEffect(() => {
    alertService.getAnalytics().then(setAnalytics).catch(() => {});
  }, []);

  /** 处理解决告警 */
  const handleResolve = async () => {
    if (!currentAlert) return;
    try {
      await alertService.resolve(currentAlert.id, resolveForm);
      message.success('告警已解决');
      setResolveModalOpen(false);
      setResolveForm({ resolution: '', resolvedBy: '' });
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  /** 处理分配 */
  const handleAssign = async () => {
    if (!currentAlert) return;
    try {
      await alertService.assign(currentAlert.id, assignTo);
      message.success('告警已分配');
      setAssignModalOpen(false);
      setAssignTo('');
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  /** 处理升级 */
  const handleEscalate = async () => {
    if (!currentAlert) return;
    try {
      await alertService.escalate(currentAlert.id, escalateReason);
      message.success('告警已升级');
      setEscalateModalOpen(false);
      setEscalateReason('');
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  /** 取消升级 */
  const handleCancelEscalation = async (alert: Alert) => {
    try {
      await alertService.cancelEscalation(alert.id);
      message.success('已取消升级');
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  const columns: ProColumns<Alert>[] = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      search: false,
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      width: 200,
      search: false,
    },
    {
      title: '严重程度',
      dataIndex: 'severity',
      width: 100,
      valueType: 'select',
      valueEnum: {
        CRITICAL: { text: '严重', status: 'Error' },
        HIGH: { text: '高危', status: 'Warning' },
        MEDIUM: { text: '中危', status: 'Processing' },
        LOW: { text: '低危', status: 'Default' },
        INFO: { text: '信息', status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: {
        NEW: { text: '新建' },
        DEDUPLICATED: { text: '去重' },
        ENRICHED: { text: '已丰富' },
        NOTIFIED: { text: '已通知' },
        ESCALATED: { text: '已升级' },
        RESOLVED: { text: '已解决' },
        ARCHIVED: { text: '已归档' },
      },
      render: (_, record) => (
        <Tag color={statusColorMap[record.status]}>
          {statusLabelMap[record.status] || record.status}
        </Tag>
      ),
    },
    {
      title: '攻击者MAC',
      dataIndex: 'attackMac',
      width: 150,
      search: false,
      render: (mac) => mac ? <code>{mac as string}</code> : '-',
    },
    {
      title: '威胁分数',
      dataIndex: 'threatScore',
      width: 100,
      search: false,
      render: (score) => (score as number)?.toFixed(2) || '-',
    },
    {
      title: '升级等级',
      dataIndex: 'escalationLevel',
      width: 80,
      search: false,
      render: (level) => (level as number) > 0 ? <Tag color="volcano">L{level as number}</Tag> : '-',
    },
    {
      title: '分配给',
      dataIndex: 'assignedTo',
      width: 120,
      search: false,
      render: (val) => val || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, record) =>
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      width: 220,
      key: 'actions',
      search: false,
      render: (_, record) => (
        <Space size="small">
          {record.status !== AlertStatus.RESOLVED && record.status !== AlertStatus.ARCHIVED && (
            <>
              <Tooltip title="解决">
                <Button
                  type="link"
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => {
                    setCurrentAlert(record);
                    setResolveModalOpen(true);
                  }}
                />
              </Tooltip>
              <Tooltip title="分配">
                <Button
                  type="link"
                  size="small"
                  icon={<UserOutlined />}
                  onClick={() => {
                    setCurrentAlert(record);
                    setAssignModalOpen(true);
                  }}
                />
              </Tooltip>
              {record.status !== AlertStatus.ESCALATED ? (
                <Tooltip title="升级">
                  <Button
                    type="link"
                    size="small"
                    danger
                    icon={<ArrowUpOutlined />}
                    onClick={() => {
                      setCurrentAlert(record);
                      setEscalateModalOpen(true);
                    }}
                  />
                </Tooltip>
              ) : (
                <Tooltip title="取消升级">
                  <Button
                    type="link"
                    size="small"
                    icon={<StopOutlined />}
                    onClick={() => handleCancelEscalation(record)}
                  />
                </Tooltip>
              )}
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* 统计卡片 */}
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="总告警数"
              value={analytics?.totalAlerts || 0}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="已解决"
              value={analytics?.resolvedAlerts || 0}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="已升级"
              value={analytics?.escalatedAlerts || 0}
              valueStyle={{ color: '#cf1322' }}
              prefix={<ArrowUpOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="平均解决时间"
              value={analytics?.averageResolutionTime ? `${Math.round(analytics.averageResolutionTime / 60)}min` : '-'}
            />
          </Card>
        </Col>
      </Row>

      {/* 告警列表 */}
      <ProTable<Alert>
        headerTitle="告警列表"
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          try {
            const result = await alertService.getAlerts({
              page: (params.current || 1) - 1,
              size: params.pageSize || 20,
              status: params.status,
              severity: params.severity,
              sortBy: 'created_at',
              sortDir: 'DESC',
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
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => {
              actionRef.current?.reload();
              alertService.getAnalytics().then(setAnalytics).catch(() => {});
            }}
          >
            刷新
          </Button>,
        ]}
      />

      {/* 解决告警弹窗 */}
      <Modal
        title="解决告警"
        open={resolveModalOpen}
        onOk={handleResolve}
        onCancel={() => setResolveModalOpen(false)}
        okText="确认解决"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input
            placeholder="解决方案"
            value={resolveForm.resolution}
            onChange={(e) => setResolveForm({ ...resolveForm, resolution: e.target.value })}
          />
          <Input
            placeholder="处理人"
            value={resolveForm.resolvedBy}
            onChange={(e) => setResolveForm({ ...resolveForm, resolvedBy: e.target.value })}
          />
        </Space>
      </Modal>

      {/* 分配告警弹窗 */}
      <Modal
        title="分配告警"
        open={assignModalOpen}
        onOk={handleAssign}
        onCancel={() => setAssignModalOpen(false)}
        okText="确认分配"
      >
        <Input
          placeholder="分配给"
          value={assignTo}
          onChange={(e) => setAssignTo(e.target.value)}
        />
      </Modal>

      {/* 升级告警弹窗 */}
      <Modal
        title="升级告警"
        open={escalateModalOpen}
        onOk={handleEscalate}
        onCancel={() => setEscalateModalOpen(false)}
        okText="确认升级"
      >
        <Input.TextArea
          placeholder="升级原因"
          rows={3}
          value={escalateReason}
          onChange={(e) => setEscalateReason(e.target.value)}
        />
      </Modal>
    </Space>
  );
};

export default AlertCenter;
