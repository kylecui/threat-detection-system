import { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Space, Modal, message } from 'antd';
import { DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import type { ThreatAssessment } from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import dayjs from 'dayjs';

/**
 * 威胁列表页面
 */
const ThreatList = () => {
  const [loading, setLoading] = useState(false);
  const [threats, setThreats] = useState<ThreatAssessment[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  /**
   * 加载威胁列表
   */
  const loadThreats = async () => {
    try {
      setLoading(true);
      const response = await threatService.getThreatList({
        page: page - 1,  // Spring Data页码从0开始
        page_size: pageSize,
        sort_by: 'assessmentTime',
        sort_order: 'desc',
      });
      setThreats(response.content || []);
      setTotal(response.totalElements || 0);
    } catch (error) {
      message.error('加载威胁列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadThreats();
  }, [page, pageSize]);

  /**
   * 删除威胁
   */
  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: '确定要删除这条威胁记录吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await threatService.deleteThreat(id);
          message.success('删除成功');
          loadThreats();
        } catch (error) {
          message.error('删除失败');
        }
      },
    });
  };

  /**
   * 威胁等级标签
   */
  const getThreatLevelTag = (level: ThreatLevel) => {
    const colorMap = {
      [ThreatLevel.CRITICAL]: 'red',
      [ThreatLevel.HIGH]: 'orange',
      [ThreatLevel.MEDIUM]: 'gold',
      [ThreatLevel.LOW]: 'blue',
      [ThreatLevel.INFO]: 'default',
    };
    return <Tag color={colorMap[level]}>{level}</Tag>;
  };

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '评估时间',
      dataIndex: 'assessmentTime',
      key: 'assessmentTime',
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
      width: 180,
    },
    {
      title: '攻击者MAC',
      dataIndex: 'attackMac',
      key: 'attackMac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: '威胁等级',
      dataIndex: 'threatLevel',
      key: 'threatLevel',
      render: (level: ThreatLevel) => getThreatLevelTag(level),
      width: 100,
    },
    {
      title: '威胁分数',
      dataIndex: 'threatScore',
      key: 'threatScore',
      render: (score: number) => score?.toFixed(2) || 'N/A',
      width: 100,
    },
    {
      title: '攻击次数',
      dataIndex: 'attackCount',
      key: 'attackCount',
      width: 100,
    },
    {
      title: '诱饵IP数',
      dataIndex: 'uniqueIps',
      key: 'uniqueIps',
      width: 100,
    },
    {
      title: '端口种类',
      dataIndex: 'uniquePorts',
      key: 'uniquePorts',
      width: 100,
    },
    {
      title: '设备数',
      dataIndex: 'uniqueDevices',
      key: 'uniqueDevices',
      width: 80,
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ThreatAssessment) => (
        <Button
          type="link"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(record.id)}
        >
          删除
        </Button>
      ),
      width: 100,
    },
  ];

  return (
    <Card title="威胁列表" bordered={false}>
      <Table
        columns={columns}
        dataSource={threats}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (newPage, newPageSize) => {
            setPage(newPage);
            setPageSize(newPageSize);
          },
        }}
      />
    </Card>
  );
};

export default ThreatList;
