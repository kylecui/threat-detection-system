import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  TeamOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { usePermission } from '@/hooks/usePermission';
import {
  addGroupMember,
  batchAssignLockMode,
  createEntityGroup,
  deleteEntityGroup,
  getEntityLocks,
  listEntityGroups,
  listGroupMembers,
  removeAllLocks,
  removeGroupMember,
  removeLock,
  setLockMode,
  updateEntityGroup,
} from '@/services/configCascading';
import type { ConfigInheritanceItem, EntityGroup, EntityGroupMember } from '@/services/configCascading';

type EntityType = 'tenant' | 'customer';
type GroupType = 'tenant_group' | 'customer_group';
type LockMode = 'default' | 'inherit_only' | 'independent_only';

const CONFIG_TYPE_OPTIONS = ['llm', 'tire', 'detection_rules', 'alert_thresholds', 'notification'];

const ConfigCascading = () => {
  const { t } = useTranslation();
  const [lockQueryForm] = Form.useForm();
  const [lockForm] = Form.useForm();
  const [groupForm] = Form.useForm();
  const [memberForm] = Form.useForm();
  const [batchForm] = Form.useForm();

  const [lockItems, setLockItems] = useState<ConfigInheritanceItem[]>([]);
  const [lockLoading, setLockLoading] = useState(false);
  const [lockModalOpen, setLockModalOpen] = useState(false);
  const [editingLock, setEditingLock] = useState<ConfigInheritanceItem | null>(null);
  const [currentEntityType, setCurrentEntityType] = useState<EntityType | null>(null);
  const [currentEntityId, setCurrentEntityId] = useState<number | null>(null);

  const [groups, setGroups] = useState<EntityGroup[]>([]);
  const [groupLoading, setGroupLoading] = useState(false);
  const [groupModalOpen, setGroupModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<EntityGroup | null>(null);

  const [membersModalOpen, setMembersModalOpen] = useState(false);
  const [membersLoading, setMembersLoading] = useState(false);
  const [members, setMembers] = useState<EntityGroupMember[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<EntityGroup | null>(null);

  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [batchSubmitting, setBatchSubmitting] = useState(false);

  const { isAdminRole: isAdmin } = usePermission();

  const lockModeLabel = (mode: LockMode) => {
    if (mode === 'inherit_only') return t('configCascading.lockModeInheritOnly');
    if (mode === 'independent_only') return t('configCascading.lockModeIndependentOnly');
    return t('configCascading.lockModeDefault');
  };

  const lockModeColor = (mode: LockMode) => {
    if (mode === 'inherit_only') return 'red';
    if (mode === 'independent_only') return 'green';
    return 'blue';
  };

  const loadGroups = useCallback(async () => {
    try {
      setGroupLoading(true);
      const data = await listEntityGroups();
      setGroups(data);
    } catch {
      message.error(t('configCascading.loadGroupsFailed'));
    } finally {
      setGroupLoading(false);
    }
  }, [t]);

  const loadLocks = useCallback(async (entityType: EntityType, entityId: number) => {
    try {
      setLockLoading(true);
      const data = await getEntityLocks(entityType, entityId);
      setLockItems(data);
      setCurrentEntityType(entityType);
      setCurrentEntityId(entityId);
    } catch {
      message.error(t('configCascading.loadLocksFailed'));
    } finally {
      setLockLoading(false);
    }
  }, [t]);

  const loadMembers = useCallback(async (group: EntityGroup) => {
    try {
      setMembersLoading(true);
      const data = await listGroupMembers(group.id);
      setMembers(data);
      setSelectedGroup(group);
      setMembersModalOpen(true);
    } catch {
      message.error(t('configCascading.loadMembersFailed'));
    } finally {
      setMembersLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!isAdmin) return;
    loadGroups();
  }, [isAdmin, loadGroups]);

  const handleLoadLocks = async () => {
    const values = await lockQueryForm.validateFields();
    await loadLocks(values.entityType as EntityType, Number(values.entityId));
  };

  const openAddLockModal = () => {
    if (!currentEntityType || currentEntityId === null) {
      message.warning(t('configCascading.selectEntityFirst'));
      return;
    }
    setEditingLock(null);
    lockForm.setFieldsValue({
      configType: undefined,
      lockMode: 'default',
    });
    setLockModalOpen(true);
  };

  const openEditLockModal = (item: ConfigInheritanceItem) => {
    setEditingLock(item);
    lockForm.setFieldsValue({
      configType: item.configType,
      lockMode: item.lockMode,
    });
    setLockModalOpen(true);
  };

  const handleSaveLock = async () => {
    if (!currentEntityType || currentEntityId === null) {
      message.warning(t('configCascading.selectEntityFirst'));
      return;
    }

    try {
      const values = await lockForm.validateFields();
      await setLockMode(
        currentEntityType,
        currentEntityId,
        values.configType as string,
        values.lockMode as LockMode,
      );
      message.success(t('configCascading.lockSaved'));
      setLockModalOpen(false);
      lockForm.resetFields();
      setEditingLock(null);
      await loadLocks(currentEntityType, currentEntityId);
    } catch {
      message.error(t('configCascading.saveLockFailed'));
    }
  };

  const handleRemoveLock = (item: ConfigInheritanceItem) => {
    Modal.confirm({
      title: t('configCascading.confirmRemoveLock', { configType: item.configType }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        if (!currentEntityType || currentEntityId === null) return;
        await removeLock(currentEntityType, currentEntityId, item.configType);
        message.success(t('configCascading.lockRemoved'));
        await loadLocks(currentEntityType, currentEntityId);
      },
    });
  };

  const handleRemoveAllLocks = () => {
    if (!currentEntityType || currentEntityId === null) {
      message.warning(t('configCascading.selectEntityFirst'));
      return;
    }

    Modal.confirm({
      title: t('configCascading.confirmRemoveAllLocks'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        if (!currentEntityType || currentEntityId === null) return;
        await removeAllLocks(currentEntityType, currentEntityId);
        message.success(t('configCascading.allLocksRemoved'));
        await loadLocks(currentEntityType, currentEntityId);
      },
    });
  };

  const openCreateGroupModal = () => {
    setEditingGroup(null);
    groupForm.setFieldsValue({
      groupName: '',
      groupType: 'tenant_group',
      description: '',
    });
    setGroupModalOpen(true);
  };

  const openEditGroupModal = (group: EntityGroup) => {
    setEditingGroup(group);
    groupForm.setFieldsValue({
      groupName: group.groupName,
      groupType: group.groupType,
      description: group.description || '',
    });
    setGroupModalOpen(true);
  };

  const handleSaveGroup = async () => {
    try {
      const values = await groupForm.validateFields();
      const payload = {
        groupName: values.groupName as string,
        groupType: values.groupType as GroupType,
        description: (values.description as string) || null,
      };

      if (editingGroup) {
        await updateEntityGroup(editingGroup.id, payload);
        message.success(t('configCascading.groupUpdated'));
      } else {
        await createEntityGroup(payload);
        message.success(t('configCascading.groupCreated'));
      }

      setGroupModalOpen(false);
      groupForm.resetFields();
      setEditingGroup(null);
      await loadGroups();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const handleDeleteGroup = (group: EntityGroup) => {
    Modal.confirm({
      title: t('configCascading.confirmDeleteGroup', { name: group.groupName }),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        await deleteEntityGroup(group.id);
        message.success(t('configCascading.groupDeleted'));
        await loadGroups();
      },
    });
  };

  const handleAddMember = async () => {
    if (!selectedGroup) return;
    try {
      const values = await memberForm.validateFields();
      await addGroupMember(selectedGroup.id, Number(values.entityId), values.entityType as EntityType);
      message.success(t('configCascading.memberAdded'));
      memberForm.resetFields();
      await loadMembers(selectedGroup);
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const handleRemoveMember = (member: EntityGroupMember) => {
    Modal.confirm({
      title: t('configCascading.confirmRemoveMember'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      onOk: async () => {
        if (!selectedGroup) return;
        await removeGroupMember(selectedGroup.id, member.entityId, member.entityType);
        message.success(t('configCascading.memberRemoved'));
        await loadMembers(selectedGroup);
      },
    });
  };

  const openBatchAssignModal = (group: EntityGroup) => {
    setSelectedGroup(group);
    batchForm.setFieldsValue({
      configType: undefined,
      lockMode: 'default',
    });
    setBatchModalOpen(true);
  };

  const handleBatchAssign = async () => {
    if (!selectedGroup) return;
    try {
      const values = await batchForm.validateFields();
      setBatchSubmitting(true);
      await batchAssignLockMode(
        selectedGroup.id,
        values.configType as string,
        values.lockMode as LockMode,
      );
      message.success(t('configCascading.batchAssignSuccess'));
      setBatchModalOpen(false);
      batchForm.resetFields();
    } catch {
      message.error(t('configCascading.batchAssignFailed'));
    } finally {
      setBatchSubmitting(false);
    }
  };

  const lockColumns: ColumnsType<ConfigInheritanceItem> = [
    {
      title: t('configCascading.configType'),
      dataIndex: 'configType',
      key: 'configType',
    },
    {
      title: t('configCascading.lockMode'),
      dataIndex: 'lockMode',
      key: 'lockMode',
      render: (value: LockMode) => <Tag color={lockModeColor(value)}>{lockModeLabel(value)}</Tag>,
    },
    {
      title: t('configCascading.lockedBy'),
      dataIndex: 'lockedBy',
      key: 'lockedBy',
      render: (value: number | null) => (value === null ? '-' : value),
    },
    {
      title: t('configCascading.lockedAt'),
      dataIndex: 'lockedAt',
      key: 'lockedAt',
      render: (value: string | null) => value || '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: ConfigInheritanceItem) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => openEditLockModal(record)}>
            {t('common.edit')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleRemoveLock(record)}>
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  const groupColumns: ColumnsType<EntityGroup> = [
    {
      title: t('configCascading.groupName'),
      dataIndex: 'groupName',
      key: 'groupName',
    },
    {
      title: t('configCascading.groupType'),
      dataIndex: 'groupType',
      key: 'groupType',
      render: (value: GroupType) => (
        <Tag color={value === 'tenant_group' ? 'blue' : 'purple'}>
          {value === 'tenant_group' ? t('configCascading.tenantGroup') : t('configCascading.customerGroup')}
        </Tag>
      ),
    },
    {
      title: t('configCascading.description'),
      dataIndex: 'description',
      key: 'description',
      render: (value: string | null) => value || '-',
    },
    {
      title: t('common.tenant'),
      dataIndex: 'tenantId',
      key: 'tenantId',
      render: (value: number | null) => (value === null ? '-' : value),
    },
    {
      title: t('configCascading.lockedBy'),
      dataIndex: 'createdBy',
      key: 'createdBy',
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: EntityGroup) => (
        <Space wrap>
          <Button icon={<EditOutlined />} onClick={() => openEditGroupModal(record)}>
            {t('common.edit')}
          </Button>
          <Button icon={<TeamOutlined />} onClick={() => loadMembers(record)}>
            {t('configCascading.manageMembers')}
          </Button>
          <Button icon={<LockOutlined />} onClick={() => openBatchAssignModal(record)}>
            {t('configCascading.batchAssign')}
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDeleteGroup(record)}>
            {t('common.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  const memberColumns: ColumnsType<EntityGroupMember> = [
    {
      title: t('configCascading.entityId'),
      dataIndex: 'entityId',
      key: 'entityId',
    },
    {
      title: t('configCascading.entityType'),
      dataIndex: 'entityType',
      key: 'entityType',
      render: (value: EntityType) => (value === 'tenant' ? t('configCascading.tenant') : t('configCascading.customer')),
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'addedAt',
      key: 'addedAt',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      render: (_: unknown, record: EntityGroupMember) => (
        <Button danger icon={<DeleteOutlined />} onClick={() => handleRemoveMember(record)}>
          {t('configCascading.removeMember')}
        </Button>
      ),
    },
  ];

  if (!isAdmin) {
    return <Alert type="warning" showIcon message={t('apiErrors.forbidden')} />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card bordered={false}>
        <Divider orientation="left">{t('configCascading.lockManagement')}</Divider>
        <Form form={lockQueryForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item
            name="entityType"
            label={t('configCascading.entityType')}
            rules={[{ required: true, message: t('configCascading.selectEntityFirst') }]}
          >
            <Select
              style={{ width: 180 }}
              options={[
                { label: t('configCascading.tenant'), value: 'tenant' },
                { label: t('configCascading.customer'), value: 'customer' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="entityId"
            label={t('configCascading.entityId')}
            rules={[{ required: true, message: t('configCascading.selectEntityFirst') }]}
          >
            <Input style={{ width: 180 }} type="number" min={1} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={handleLoadLocks} icon={<UnlockOutlined />}>
                {t('configCascading.loadLocks')}
              </Button>
              <Button icon={<PlusOutlined />} onClick={openAddLockModal}>
                {t('configCascading.addLock')}
              </Button>
              <Button danger icon={<DeleteOutlined />} onClick={handleRemoveAllLocks}>
                {t('configCascading.removeAllLocks')}
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Table<ConfigInheritanceItem>
          rowKey="id"
          loading={lockLoading}
          columns={lockColumns}
          dataSource={lockItems}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Card bordered={false}>
        <Divider orientation="left">{t('configCascading.groupManagement')}</Divider>
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateGroupModal}>
            {t('configCascading.createGroup')}
          </Button>
        </Space>
        <Table<EntityGroup>
          rowKey="id"
          loading={groupLoading}
          columns={groupColumns}
          dataSource={groups}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title={editingLock ? t('configCascading.editLock') : t('configCascading.addLock')}
        open={lockModalOpen}
        onCancel={() => {
          setLockModalOpen(false);
          lockForm.resetFields();
          setEditingLock(null);
        }}
        onOk={handleSaveLock}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={lockForm} layout="vertical">
          <Form.Item
            name="configType"
            label={t('configCascading.configType')}
            rules={[{ required: true, message: t('configCascading.configType') }]}
          >
            <Select
              disabled={!!editingLock}
              options={CONFIG_TYPE_OPTIONS.map((value) => ({ label: value, value }))}
            />
          </Form.Item>
          <Form.Item
            name="lockMode"
            label={t('configCascading.lockMode')}
            rules={[{ required: true, message: t('configCascading.lockMode') }]}
          >
            <Select
              options={[
                { label: t('configCascading.lockModeDefault'), value: 'default' },
                { label: t('configCascading.lockModeInheritOnly'), value: 'inherit_only' },
                { label: t('configCascading.lockModeIndependentOnly'), value: 'independent_only' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingGroup ? t('configCascading.editGroup') : t('configCascading.createGroup')}
        open={groupModalOpen}
        onCancel={() => {
          setGroupModalOpen(false);
          groupForm.resetFields();
          setEditingGroup(null);
        }}
        onOk={handleSaveGroup}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <Form form={groupForm} layout="vertical">
          <Form.Item
            name="groupName"
            label={t('configCascading.groupName')}
            rules={[{ required: true, message: t('configCascading.groupName') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="groupType"
            label={t('configCascading.groupType')}
            rules={[{ required: true, message: t('configCascading.groupType') }]}
          >
            <Select
              options={[
                { label: t('configCascading.tenantGroup'), value: 'tenant_group' },
                { label: t('configCascading.customerGroup'), value: 'customer_group' },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label={t('configCascading.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`${t('configCascading.manageMembers')} - ${selectedGroup?.groupName || ''}`}
        open={membersModalOpen}
        width={900}
        onCancel={() => {
          setMembersModalOpen(false);
          memberForm.resetFields();
        }}
        footer={null}
      >
        <Divider orientation="left">{t('configCascading.members')}</Divider>
        <Table<EntityGroupMember>
          rowKey="id"
          loading={membersLoading}
          columns={memberColumns}
          dataSource={members}
          pagination={{ pageSize: 8 }}
          style={{ marginBottom: 16 }}
        />

        <Divider orientation="left">{t('configCascading.addMember')}</Divider>
        <Form form={memberForm} layout="inline">
          <Form.Item
            name="entityId"
            label={t('configCascading.entityId')}
            rules={[{ required: true, message: t('configCascading.entityId') }]}
          >
            <Input type="number" min={1} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="entityType"
            label={t('configCascading.entityType')}
            rules={[{ required: true, message: t('configCascading.entityType') }]}
          >
            <Select
              style={{ width: 160 }}
              options={[
                { label: t('configCascading.tenant'), value: 'tenant' },
                { label: t('configCascading.customer'), value: 'customer' },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddMember}>
              {t('configCascading.addMember')}
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('configCascading.batchAssignTitle')}
        open={batchModalOpen}
        onCancel={() => {
          setBatchModalOpen(false);
          batchForm.resetFields();
        }}
        onOk={handleBatchAssign}
        confirmLoading={batchSubmitting}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <Form form={batchForm} layout="vertical">
          <Form.Item
            name="configType"
            label={t('configCascading.configType')}
            rules={[{ required: true, message: t('configCascading.configType') }]}
          >
            <Select options={CONFIG_TYPE_OPTIONS.map((value) => ({ label: value, value }))} />
          </Form.Item>
          <Form.Item
            name="lockMode"
            label={t('configCascading.lockMode')}
            rules={[{ required: true, message: t('configCascading.lockMode') }]}
          >
            <Select
              options={[
                { label: t('configCascading.lockModeDefault'), value: 'default' },
                { label: t('configCascading.lockModeInheritOnly'), value: 'inherit_only' },
                { label: t('configCascading.lockModeIndependentOnly'), value: 'independent_only' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};

export default ConfigCascading;
