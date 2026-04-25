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
import { useTranslation } from 'react-i18next';
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

/**
 * 告警中心页面
 */
const AlertCenter = () => {
  const { t } = useTranslation();
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
      message.success(t('alertCenter.messageResolved'));
      setResolveModalOpen(false);
      setResolveForm({ resolution: '', resolvedBy: '' });
      actionRef.current?.reload();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  /** 处理分配 */
  const handleAssign = async () => {
    if (!currentAlert) return;
    try {
      await alertService.assign(currentAlert.id, assignTo);
      message.success(t('alertCenter.messageAssigned'));
      setAssignModalOpen(false);
      setAssignTo('');
      actionRef.current?.reload();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  /** 处理升级 */
  const handleEscalate = async () => {
    if (!currentAlert) return;
    try {
      await alertService.escalate(currentAlert.id, escalateReason);
      message.success(t('alertCenter.messageEscalated'));
      setEscalateModalOpen(false);
      setEscalateReason('');
      actionRef.current?.reload();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  /** 取消升级 */
  const handleCancelEscalation = async (alert: Alert) => {
    try {
      await alertService.cancelEscalation(alert.id);
      message.success(t('alertCenter.messageEscalationCancelled'));
      actionRef.current?.reload();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const columns: ProColumns<Alert>[] = [
    {
      title: t('common.id'),
      dataIndex: 'id',
      width: 70,
      search: false,
    },
    {
      title: t('alertCenter.titleColumn'),
      dataIndex: 'title',
      ellipsis: true,
      width: 200,
      search: false,
    },
    {
      title: t('common.severity'),
      dataIndex: 'severity',
      width: 100,
      valueType: 'select',
      valueEnum: {
        CRITICAL: { text: t('common.severityCritical'), status: 'Error' },
        HIGH: { text: t('common.severityHighRisk'), status: 'Warning' },
        MEDIUM: { text: t('common.severityMediumRisk'), status: 'Processing' },
        LOW: { text: t('common.severityLowRisk'), status: 'Default' },
        INFO: { text: t('common.severityInfo'), status: 'Default' },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: {
        NEW: { text: t('alertCenter.statusNew') },
        DEDUPLICATED: { text: t('alertCenter.statusDeduplicated') },
        ENRICHED: { text: t('alertCenter.statusEnriched') },
        NOTIFIED: { text: t('alertCenter.statusNotified') },
        ESCALATED: { text: t('alertCenter.statusEscalated') },
        RESOLVED: { text: t('alertCenter.statusResolved') },
        ARCHIVED: { text: t('alertCenter.statusArchived') },
      },
      render: (_, record) => (
        <Tag color={statusColorMap[record.status]}>
          {t(`alertCenter.status.${record.status}`, { defaultValue: record.status })}
        </Tag>
      ),
    },
    {
      title: t('alertCenter.attackMac'),
      dataIndex: 'attackMac',
      width: 150,
      search: false,
      render: (mac) => mac ? <code>{mac as string}</code> : '-',
    },
    {
      title: t('alertCenter.threatScore'),
      dataIndex: 'threatScore',
      width: 100,
      search: false,
      render: (score) => (score as number)?.toFixed(2) || '-',
    },
    {
      title: t('alertCenter.escalationLevel'),
      dataIndex: 'escalationLevel',
      width: 80,
      search: false,
      render: (level) => (level as number) > 0 ? <Tag color="volcano">L{level as number}</Tag> : '-',
    },
    {
      title: t('alertCenter.assignedTo'),
      dataIndex: 'assignedTo',
      width: 120,
      search: false,
      render: (val) => val || '-',
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, record) =>
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: t('common.actions'),
      width: 220,
      key: 'actions',
      search: false,
      render: (_, record) => (
        <Space size="small">
          {record.status !== AlertStatus.RESOLVED && record.status !== AlertStatus.ARCHIVED && (
            <>
              <Tooltip title={t('alertCenter.resolve')}>
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
              <Tooltip title={t('alertCenter.assign')}>
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
                <Tooltip title={t('alertCenter.escalate')}>
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
                <Tooltip title={t('alertCenter.cancelEscalation')}>
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
              title={t('alertCenter.totalAlerts')}
              value={analytics?.totalAlerts || 0}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('alertCenter.resolved')}
              value={analytics?.resolvedAlerts || 0}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('alertCenter.escalated')}
              value={analytics?.escalatedAlerts || 0}
              valueStyle={{ color: '#cf1322' }}
              prefix={<ArrowUpOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('alertCenter.avgResolutionTime')}
              value={analytics?.averageResolutionTime ? `${Math.round(analytics.averageResolutionTime / 60)}${t('common.minuteShort')}` : '-'}
            />
          </Card>
        </Col>
      </Row>

      {/* 告警列表 */}
      <ProTable<Alert>
        headerTitle={t('alertCenter.listTitle')}
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
              {t('common.refresh')}
          </Button>,
        ]}
      />

      {/* 解决告警弹窗 */}
      <Modal
        title={t('alertCenter.resolveAlert')}
        open={resolveModalOpen}
        onOk={handleResolve}
        onCancel={() => setResolveModalOpen(false)}
        okText={t('alertCenter.confirmResolve')}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input
            placeholder={t('alertCenter.resolution')}
            value={resolveForm.resolution}
            onChange={(e) => setResolveForm({ ...resolveForm, resolution: e.target.value })}
          />
          <Input
            placeholder={t('alertCenter.resolvedBy')}
            value={resolveForm.resolvedBy}
            onChange={(e) => setResolveForm({ ...resolveForm, resolvedBy: e.target.value })}
          />
        </Space>
      </Modal>

      {/* 分配告警弹窗 */}
      <Modal
        title={t('alertCenter.assignAlert')}
        open={assignModalOpen}
        onOk={handleAssign}
        onCancel={() => setAssignModalOpen(false)}
        okText={t('alertCenter.confirmAssign')}
      >
        <Input
          placeholder={t('alertCenter.assignedTo')}
          value={assignTo}
          onChange={(e) => setAssignTo(e.target.value)}
        />
      </Modal>

      {/* 升级告警弹窗 */}
      <Modal
        title={t('alertCenter.escalateAlert')}
        open={escalateModalOpen}
        onOk={handleEscalate}
        onCancel={() => setEscalateModalOpen(false)}
        okText={t('alertCenter.confirmEscalate')}
      >
        <Input.TextArea
          placeholder={t('alertCenter.escalationReason')}
          rows={3}
          value={escalateReason}
          onChange={(e) => setEscalateReason(e.target.value)}
        />
      </Modal>
    </Space>
  );
};

export default AlertCenter;
