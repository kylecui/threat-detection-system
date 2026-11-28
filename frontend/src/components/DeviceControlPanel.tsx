import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Collapse,
  Descriptions,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import {
  createUuidV4,
  deviceControl,
  type ControlEvent,
  type ControlRecord,
} from "@/services/deviceControl";
import PermissionGate from "./PermissionGate";
import {
  controlPhases,
  controlResult,
  isConfigStateAction,
} from "@/services/controlResults";
import DeviceControlOperations, {
  controlWriters,
} from "./DeviceControlOperations";

type ConfigDocument = {
  id: number;
  yamlText: string;
  sha256?: string;
  status?: string;
  configVersion?: number;
};
const pending = new Set([
  "queued",
  "broker_acked",
  "accepted",
  "publish_unknown",
  "device_pending_confirm",
]);
const phaseLabels: Record<string, string> = {
  queued: "请求已保存",
  broker_acked: "Broker已接收，等待执行",
  accepted: "任务已受理，等待设备回执",
  completed: "查询或草稿操作完成",
  device_applied: "设备执行成功",
  device_rejected: "设备拒绝",
  rejected: "请求被拒绝",
  timed_out: "设备回执超时",
  cancelled: "任务已取消",
  publish_unknown: "发送结果未知，请查询",
  execution_unknown: "执行结果未知，需核对",
  sent_unconfirmed: "锁状态已发送，设备未确认",
  device_pending_confirm: "设备待确认",
  control_reconciliation_required: "需人工对账",
  rolled_back: "已回滚",
};
const requiredResult = new Set([
  "capabilities",
  "config.draft",
  "config.restore",
  "config.history",
  "policy.list",
  "violation.list",
  "channel.diagnostics",
]);
const activeTaskPhases = new Set([
  "accepted",
  "broker_acked",
  "publish_failed",
]);
const reviewTaskPhases = new Set([
  "device_pending_confirm",
  "control_reconciliation_required",
  "device_rejected",
  "timed_out",
]);

/** Same Ant Design controls as the existing device drawer. No browser MQTT credentials. */
export default function DeviceControlPanel({
  customerId,
  devSerial,
}: {
  customerId: string;
  devSerial: string;
}) {
  const [modal, modalHolder] = Modal.useModal();
  const [records, setRecords] = useState<ControlRecord[]>([]);
  const [ready, setReady] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);
  const [operationError, setOperationError] = useState(false);
  const [yaml, setYaml] = useState("");
  const [config, setConfig] = useState<Record<string, any>>({});
  const [revision, setRevision] = useState<number>();
  const [editBase, setEditBase] = useState({ etag: 0, token: "" });
  const [baseToken, setBaseToken] = useState("");
  const [dirty, setDirty] = useState(false);
  const [resultError, setResultError] = useState(false);
  const [presence, setPresence] = useState<ControlEvent>();
  const [newViolation, setNewViolation] = useState(false);
  const [eventReads, setEventReads] = useState<string[]>([]);
  const scopeKey = `${customerId}\0${devSerial}`;
  const scope = useRef(scopeKey);
  const contextEpoch = useRef(0);
  if (scope.current !== scopeKey) {
    contextEpoch.current += 1;
    scope.current = scopeKey;
  }
  const pageEpoch = contextEpoch.current;
  const mounted = useRef(true);
  const editSeq = useRef(0);
  const dirtyRef = useRef(false);
  const busyRef = useRef(false);
  const loadSeq = useRef(0);
  const snapshots = useRef(
    new Map<
      string,
      { scope: string; seq: number; text: string; action: string }
    >(),
  );
  const replaceText = (value: string, unsaved = true) => {
    editSeq.current += 1;
    dirtyRef.current = unsaved;
    setDirty(unsaved);
    setYaml(value);
  };
  const [reason, setReason] = useState("");
  const [candidate, setCandidate] = useState<number | null>(null);
  const [batch, setBatch] = useState<string>();
  const [actions, setActions] = useState<string[]>([]);
  const applied = useRef(new Set<string>());
  const retry = useRef<{ fingerprint: string; key: string }>();

  const load = useCallback(
    async (signal?: AbortSignal) => {
      const requestScope = scopeKey;
      const requestEpoch = contextEpoch.current;
      const requestSeq = ++loadSeq.current;
      try {
        const [status, history] = await Promise.all([
          deviceControl.readiness(customerId, devSerial, signal),
          deviceControl.history(customerId, devSerial, signal),
        ]);
        if (
          signal?.aborted ||
          !mounted.current ||
          scope.current !== requestScope ||
          contextEpoch.current !== requestEpoch ||
          requestSeq !== loadSeq.current
        )
          return;
        setReady(status.ready);
        setEnabled(status.enabled);
        setRecords(history);
        setStale(false);
        let decodeFailed = history.some(
          (row) =>
            !!row.resultDecodeError ||
            (row.action === "candidates.pull" &&
              row.phase === "device_applied" &&
              !controlResult(row)),
        );
        for (const row of [...history].reverse()) {
          const versionKey = `${row.requestId}:${row.resultVersion ?? row.result?.resultVersion ?? 0}`;
          if (applied.current.has(versionKey) || row.phase !== "completed")
            continue;
          const result = controlResult(row);
          if (!result) {
            if (
              isConfigStateAction(row.action) ||
              requiredResult.has(row.action)
            )
              decodeFailed = true;
            continue;
          }
          const snapshot = snapshots.current.get(row.requestId);
          if (
            ["config.draft", "config.restore"].includes(row.action) &&
            !snapshot
          )
            continue;
          if (row.action === "capabilities") setActions(result.actions);
          if (isConfigStateAction(row.action)) {
            setConfig(result);
            setBaseToken(result.baseToken);
            // A server refresh must not silently rebase an unsaved YAML edit.
            if (!dirtyRef.current) {
              setEditBase({
                etag: Number(result.etag),
                token: result.baseToken,
              });
              const doc = result.draft || result.applied || result.observed;
              setYaml(doc?.yamlText || "");
              setRevision(result.draft?.id);
            }
          }
          if (
            (row.action === "config.draft" ||
              row.action === "config.restore") &&
            snapshot?.scope === requestScope
          ) {
            setRevision(result.id);
            setEditBase({ etag: result.id, token: result.baseToken });
            setBaseToken(result.baseToken);
            if (snapshot.seq === editSeq.current) {
              setYaml(
                typeof result.yamlText === "string"
                  ? result.yamlText
                  : snapshot.text,
              );
              dirtyRef.current = false;
              setDirty(false);
            }
          }
          applied.current.add(versionKey);
        }
        setResultError(decodeFailed);
      } catch {
        if (
          !signal?.aborted &&
          mounted.current &&
          scope.current === requestScope &&
          contextEpoch.current === requestEpoch &&
          requestSeq === loadSeq.current
        )
          setStale(true);
      }
    },
    [customerId, devSerial, scopeKey],
  );

  useEffect(() => {
    mounted.current = true;
    applied.current.clear();
    snapshots.current.clear();
    retry.current = undefined;
    editSeq.current = 0;
    dirtyRef.current = false;
    setDirty(false);
    setYaml("");
    setRevision(undefined);
    setEditBase({ etag: 0, token: "" });
    setBaseToken("");
    setActions([]);
    setConfig({});
    setRecords([]);
    busyRef.current = false;
    setBusy(false);
    setReady(false);
    setEnabled(false);
    setStale(false);
    setPresence(undefined);
    setNewViolation(false);
    setEventReads([]);
    setResultError(false);
    const abort = new AbortController();
    void load(abort.signal);
    const timer = window.setInterval(() => {
      void load(abort.signal);
    }, 3000);
    return () => {
      mounted.current = false;
      loadSeq.current += 1;
      abort.abort();
      window.clearInterval(timer);
    };
  }, [load]);

  useEffect(() => {
    if (!enabled) return;
    const abort = new AbortController();
    let cursor: string | undefined;
    let pulling = false;
    let notified = false;
    let reconnect: number | undefined;
    const refreshEvents = async () => {
      if (pulling) {
        notified = true;
        return;
      }
      pulling = true;
      try {
        // First load seeds the cursor and the last known presence. Event-triggered
        // refreshes only run for later events.
        for (
          let page = 0;
          page < 5 && !abort.signal.aborted && scope.current === scopeKey;
          page += 1
        ) {
          const batch = await deviceControl.events(
            customerId,
            devSerial,
            cursor,
            abort.signal,
          );
          if (abort.signal.aborted || scope.current !== scopeKey) return;
          const initial = cursor === undefined;
          cursor = batch.nextCursor;
          const types = new Set(batch.items.map((event) => event.eventType));
          const latestPresence = (
            initial ? batch.items : [...batch.items].reverse()
          ).find((event) => event.eventType === "device.presence");
          if (latestPresence) setPresence(latestPresence);
          if (!initial) {
            if (types.has("task.terminal")) void load(abort.signal);
            if (types.has("violation.created")) setNewViolation(true);
            const reads = [
              ...(types.has("config.reported") ? ["config.read"] : []),
              ...(types.has("lock.changed") ? ["lock.read"] : []),
              ...(types.has("violation.created") ? ["violation.list"] : []),
            ];
            if (reads.length)
              setEventReads((current) => [...new Set([...current, ...reads])]);
          }
          if (!batch.hasMore) break;
        }
      } catch {
        // The next poll or notification resumes from the last consumed cursor.
      } finally {
        pulling = false;
        if (notified && !abort.signal.aborted) {
          notified = false;
          void refreshEvents();
        }
      }
    };
    const connect = async () => {
      try {
        await deviceControl.subscribeEvents(
          customerId,
          devSerial,
          refreshEvents,
          abort.signal,
        );
      } catch {
        /* Cursor polling also works when streaming is unavailable. */
      }
      if (!abort.signal.aborted)
        reconnect = window.setTimeout(() => void connect(), 10000);
    };
    void refreshEvents();
    void connect();
    const timer = window.setInterval(() => void refreshEvents(), 5000);
    return () => {
      abort.abort();
      window.clearInterval(timer);
      window.clearTimeout(reconnect);
    };
  }, [enabled, customerId, devSerial, scopeKey, load]);

  const run = useCallback(
    async (action: string, params: Record<string, unknown> = {}) => {
      if (
        busyRef.current ||
        !mounted.current ||
        scope.current !== scopeKey ||
        contextEpoch.current !== pageEpoch
      )
        return;
      const requestScope = scopeKey;
      const requestEpoch = contextEpoch.current;
      const submitted = {
        scope: requestScope,
        seq: editSeq.current,
        text: yaml,
        action,
      };
      try {
        busyRef.current = true;
        setBusy(true);
        setOperationError(false);
        const fingerprint = JSON.stringify({ action, params });
        if (retry.current?.fingerprint !== fingerprint)
          retry.current = { fingerprint, key: createUuidV4() };
        const accepted = await deviceControl.submit(
          customerId,
          devSerial,
          action,
          params,
          retry.current.key,
        );
        if (
          !mounted.current ||
          scope.current !== requestScope ||
          contextEpoch.current !== requestEpoch
        )
          return;
        snapshots.current.set(accepted.requestId, submitted);
        retry.current = undefined;
        await load();
      } catch {
        if (
          mounted.current &&
          scope.current === requestScope &&
          contextEpoch.current === requestEpoch
        )
          setOperationError(true);
      } finally {
        if (
          mounted.current &&
          scope.current === requestScope &&
          contextEpoch.current === requestEpoch
        ) {
          busyRef.current = false;
          setBusy(false);
        }
      }
    },
    [customerId, devSerial, load, pageEpoch, scopeKey, yaml],
  );

  useEffect(() => {
    if (!ready || stale || busy || !actions.length || !eventReads.length)
      return;
    const next = eventReads.find((action) => actions.includes(action));
    if (next) {
      if (busyRef.current) return;
      setEventReads((items) => items.filter((action) => action !== next));
      // A fresh read can update observed state. The load handler preserves a dirty draft.
      void run(next);
    } else {
      setEventReads([]);
    }
  }, [actions, busy, eventReads, ready, run, stale]);
  const confirmAction = (options: Parameters<typeof modal.confirm>[0]) => {
    const confirmationEpoch = contextEpoch.current;
    const confirmationEdit = editSeq.current;
    return modal.confirm({
      ...options,
      onOk: () => {
        if (!mounted.current || contextEpoch.current !== confirmationEpoch)
          return;
        if (editSeq.current !== confirmationEdit) {
          setOperationError(true);
          return;
        }
        return options.onOk?.();
      },
    });
  };
  const allowed = (action: string) =>
    ready && !stale && !busy && actions.includes(action);
  const batches = records.filter(
    (row) =>
      row.action === "candidates.pull" &&
      row.phase === "device_applied" &&
      !!controlResult(row),
  );
  const selectedBatch = batches.find((row) => row.result?.taskId === batch);
  const candidates: Array<Record<string, any>> =
    selectedBatch?.result?.result?.result?.candidates || [];
  const selectedCandidate = candidates.find(
    (item) => (item.id ?? item.candidateId) === candidate,
  );
  const latest = (action: string) => {
    const row = records.find(
      (row) =>
        row.action === action &&
        row.phase === "completed" &&
        !!controlResult(row),
    );
    return row ? controlResult(row) : undefined;
  };
  const history = latest("config.history")?.items as
    | ConfigDocument[]
    | undefined;

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      {modalHolder}
      {typeof presence?.data.online === "boolean" && (
        <Tag color={presence.data.online ? "green" : "default"}>
          {presence.data.online ? "设备在线" : "设备离线"}
        </Tag>
      )}
      {newViolation && (
        <Alert
          showIcon
          closable
          type="warning"
          message="收到新的设备违规"
          onClose={() => setNewViolation(false)}
        />
      )}
      {resultError && (
        <Alert
          type="warning"
          showIcon
          message="结果读取异常，可查询原任务。编辑内容已保留。"
        />
      )}
      {dirty && (
        <Typography.Text type="warning">
          仍有未保存编辑；已保存的旧草稿不会包含这些输入。
        </Typography.Text>
      )}
      {operationError && (
        <Alert
          type="error"
          showIcon
          message="本次请求未确认。编辑内容已保留，再次提交相同操作将使用同一幂等键。"
        />
      )}
      <Alert
        type={ready && !stale ? "info" : "warning"}
        showIcon
        message={
          stale
            ? "连接状态已过期，显示最后一次结果"
            : ready
              ? "控制连接已就绪"
              : enabled
                ? "控制连接尚未就绪，请检查服务端诊断"
                : "尚未启用生产控制连接"
        }
        description="发布和受理均不表示设备已生效。请以设备回执结果为准。"
      />
      <Space wrap>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => void load()}
          disabled={busy}
        >
          刷新结果
        </Button>
        <Button
          onClick={() => void run("capabilities")}
          disabled={!ready || busy}
        >
          读取授权能力
        </Button>
        <Button
          disabled={!allowed("device.status")}
          onClick={() => void run("device.status")}
        >
          设备状态
        </Button>
        <Button
          disabled={!allowed("config.read")}
          onClick={() => void run("config.read")}
        >
          读取配置和版本
        </Button>
        <Button
          disabled={!allowed("lock.read")}
          onClick={() => void run("lock.read")}
        >
          读取锁状态
        </Button>
      </Space>
      {latest("device.status") && (
        <Descriptions
          size="small"
          column={2}
          items={Object.entries(latest("device.status")!).map(
            ([key, value]) => ({
              key,
              label: key,
              children: String(value ?? "—"),
            }),
          )}
        />
      )}
      <Typography.Text type="secondary">
        编辑基线：{editBase.etag}　草稿版本：{revision ?? "未保存"}
      </Typography.Text>
      <Space wrap>
        {(["observed", "desired", "applied", "draft"] as const).map((key) => (
          <Button
            key={key}
            disabled={!config[key]}
            onClick={() => {
              replaceText(config[key].yamlText);
              setEditBase({
                etag: Number(config.etag),
                token: config.baseToken,
              });
              setRevision(key === "draft" ? config[key].id : undefined);
            }}
          >
            {
              {
                observed: "设备报告",
                desired: "目标配置",
                applied: "已确认配置",
                draft: "编辑草稿",
              }[key]
            }
          </Button>
        ))}
      </Space>
      <Input.TextArea
        aria-label="完整YAML配置"
        value={yaml}
        onChange={(event) => replaceText(event.target.value)}
        rows={16}
        spellCheck={false}
        style={{ fontFamily: "monospace", tabSize: 2 }}
      />
      <PermissionGate
        requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN", "CUSTOMER_ADMIN"]}
      >
        <Space wrap>
          <Button
            disabled={!allowed("config.validate") || !yaml}
            onClick={() => void run("config.validate", { yaml_text: yaml })}
          >
            校验YAML
          </Button>
          <Button
            disabled={!allowed("config.draft") || !yaml || !editBase.token}
            onClick={() =>
              void run("config.draft", {
                yaml_text: yaml,
                expected_revision: editBase.etag,
                expected_base_token: editBase.token,
              })
            }
          >
            保存草稿
          </Button>
          <Button
            type="primary"
            disabled={!allowed("config.push") || !revision || dirty}
            onClick={() =>
              confirmAction({
                getContainer: false,
                title: `下发已保存草稿 #${revision}？`,
                content: "将创建新的配置版本，设备回执后才能确认生效。",
                onOk: () => run("config.push", { revision_id: revision }),
              })
            }
          >
            下发草稿
          </Button>
        </Space>
      </PermissionGate>
      <Collapse
        items={[
          {
            key: "history",
            label: "历史与恢复",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                <Button
                  disabled={!allowed("config.history")}
                  onClick={() => void run("config.history", { limit: 50 })}
                >
                  读取历史
                </Button>
                <Table
                  size="small"
                  rowKey="id"
                  dataSource={history || []}
                  pagination={{ pageSize: 5 }}
                  scroll={{ x: 580 }}
                  columns={[
                    { title: "修订", dataIndex: "id" },
                    { title: "版本", dataIndex: "configVersion" },
                    { title: "状态", dataIndex: "status" },
                    {
                      title: "操作",
                      render: (_, doc: ConfigDocument) => (
                        <Space>
                          <Button
                            size="small"
                            onClick={() => replaceText(doc.yamlText)}
                          >
                            查看
                          </Button>
                          <PermissionGate
                            requiredRoles={[
                              "SUPER_ADMIN",
                              "TENANT_ADMIN",
                              "CUSTOMER_ADMIN",
                            ]}
                          >
                            <Button
                              size="small"
                              disabled={!allowed("config.restore")}
                              onClick={() =>
                                confirmAction({
                                  getContainer: false,
                                  title: `从历史 #${doc.id} 创建新草稿？`,
                                  onOk: () =>
                                    run("config.restore", {
                                      revision_id: doc.id,
                                      expected_revision: editBase.etag,
                                      expected_base_token: editBase.token,
                                    }),
                                })
                              }
                            >
                              恢复成草稿
                            </Button>
                          </PermissionGate>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Space>
            ),
          },
          {
            key: "lock",
            label: "配置锁",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                <pre style={{ whiteSpace: "pre-wrap" }}>
                  {JSON.stringify(latest("lock.read") || {}, null, 2)}
                </pre>
                <Input
                  aria-label="锁变更原因"
                  placeholder="填写变更原因"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  maxLength={256}
                />
                <PermissionGate
                  requiredRoles={[
                    "SUPER_ADMIN",
                    "TENANT_ADMIN",
                    "CUSTOMER_ADMIN",
                  ]}
                >
                  <Space>
                    <Button
                      disabled={!allowed("lock.set") || !reason.trim()}
                      onClick={() =>
                        void run("lock.set", { reason, sections: ["base"] })
                      }
                    >
                      锁定配置
                    </Button>
                    <Button
                      disabled={!allowed("lock.clear") || !reason.trim()}
                      onClick={() => void run("lock.clear", { reason })}
                    >
                      解除锁定
                    </Button>
                  </Space>
                </PermissionGate>
              </Space>
            ),
          },
          {
            key: "candidates",
            label: "候选处置与人工审批",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                <Button
                  disabled={!allowed("candidates.pull")}
                  onClick={() => void run("candidates.pull")}
                >
                  从设备拉取候选
                </Button>
                <Typography.Text type="secondary">
                  先在下方任务结果确认候选，审批只接受5分钟内成功返回的同设备批次。
                </Typography.Text>
                <Form layout="vertical">
                  <Form.Item label="成功候选批次">
                    <Select
                      value={batch}
                      onChange={(value) => {
                        setBatch(value);
                        setCandidate(null);
                      }}
                      style={{ width: "100%" }}
                      options={batches.map((row) => ({
                        value: row.result?.taskId,
                        label: row.result?.taskId || row.requestId,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item label="待审批候选">
                    <Select
                      value={candidate}
                      onChange={setCandidate}
                      options={candidates
                        .filter(
                          (item) =>
                            item.status === "pending" && !item.mergedInto,
                        )
                        .map((item) => ({
                          value: item.id ?? item.candidateId,
                          label: `#${item.id ?? item.candidateId} · ${item.ip || item.targetIp || item.mac || item.attackMac || "请展开任务核对目标"} · ${item.suggestedMode || "arp_quarantine_host"}`,
                        }))}
                    />
                  </Form.Item>
                </Form>
                <PermissionGate
                  requiredRoles={[
                    "SUPER_ADMIN",
                    "TENANT_ADMIN",
                    "CUSTOMER_ADMIN",
                  ]}
                >
                  <Button
                    danger
                    disabled={
                      !allowed("candidates.approve") ||
                      !batch ||
                      !selectedCandidate
                    }
                    onClick={() =>
                      confirmAction({
                        getContainer: false,
                        title: `人工确认审批候选 #${candidate}？`,
                        content: (
                          <div>
                            <p>
                              设备 {devSerial}{" "}
                              将收到隔离处置命令，可能中断目标网络访问。候选批次{" "}
                              {batch}。
                            </p>
                            <pre style={{ whiteSpace: "pre-wrap" }}>
                              {JSON.stringify(selectedCandidate, null, 2)}
                            </pre>
                          </div>
                        ),
                        okText: "确认审批",
                        onOk: () =>
                          run("candidates.approve", {
                            candidate_id: candidate,
                            candidate_batch_id: batch,
                            human_confirmed: true,
                            mode:
                              selectedCandidate?.suggestedMode ||
                              "arp_quarantine_host",
                          }),
                      })
                    }
                  >
                    人工审批
                  </Button>
                </PermissionGate>
              </Space>
            ),
          },
        ]}
      />
      <DeviceControlOperations
        key={scopeKey}
        allowed={allowed}
        run={run}
        confirmAction={confirmAction}
        latest={latest}
        baseToken={baseToken}
      />
      <Table
        size="small"
        rowKey="requestId"
        dataSource={records}
        pagination={{ pageSize: 8 }}
        scroll={{ x: 640 }}
        columns={[
          { title: "动作", dataIndex: "action" },
          {
            title: "执行阶段",
            dataIndex: "phase",
            render: (_, row) => {
              return (
                <Space wrap>
                  {controlPhases(row).map((phase) => (
                    <Tag
                      key={phase}
                      color={
                        phase === "device_applied"
                          ? "green"
                          : phase === "rejected" || phase === "device_rejected"
                            ? "red"
                            : phase === "device_pending_confirm" ||
                                phase === "control_reconciliation_required" ||
                                phase === "rolled_back"
                              ? "orange"
                              : pending.has(phase)
                                ? "blue"
                                : "default"
                      }
                    >
                      {phaseLabels[phase] || phase}
                    </Tag>
                  ))}
                </Space>
              );
            },
          },
          {
            title: "操作",
            render: (_, row: ControlRecord) => {
              const phases = controlPhases(row);
              const taskId = row.result?.taskId;
              const configTask =
                row.action === "config.push" || row.action === "task.retry";
              const needsReview = phases.some((phase) =>
                reviewTaskPhases.has(phase),
              );
              const rolledBack = phases.includes("rolled_back");
              const active = activeTaskPhases.has(row.phase);
              const retryParams = baseToken
                ? { task_id: taskId, expected_base_token: baseToken }
                : { task_id: taskId };
              return (
                <Space wrap>
                  <Button
                    size="small"
                    disabled={!allowed("task.get")}
                    onClick={() =>
                      void run("task.get", { request_id: row.requestId })
                    }
                  >
                    查询真实结果
                  </Button>
                  {taskId && (
                    <PermissionGate requiredRoles={controlWriters}>
                      <Space wrap>
                        {configTask &&
                          active &&
                          !needsReview &&
                          !rolledBack && (
                            <Button
                              size="small"
                              disabled={!allowed("task.retry")}
                              onClick={() =>
                                void run("task.retry", retryParams)
                              }
                            >
                              重试任务
                            </Button>
                          )}
                        {configTask &&
                          needsReview &&
                          !rolledBack &&
                          baseToken && (
                            <Button
                              size="small"
                              disabled={!allowed("task.retry")}
                              onClick={() =>
                                confirmAction({
                                  title: "确认复核重推？",
                                  content:
                                    "配置平台将检查当前基准，通过后创建新的配置版本和任务。",
                                  okText: "确认重推",
                                  cancelText: "返回",
                                  onOk: () => run("task.retry", retryParams),
                                })
                              }
                            >
                              复核重推
                            </Button>
                          )}
                        {!rolledBack &&
                          !phases.includes("control_reconciliation_required") &&
                          ((active && !needsReview) ||
                            phases.includes("device_pending_confirm")) && (
                            <Button
                              size="small"
                              disabled={!allowed("task.cancel")}
                              onClick={() =>
                                confirmAction({
                                  title: "取消任务？",
                                  content:
                                    "取消后将停止继续投递，设备上已执行的变更仍需核对。",
                                  okText: "取消任务",
                                  cancelText: "返回",
                                  onOk: () =>
                                    run("task.cancel", { task_id: taskId }),
                                })
                              }
                            >
                              取消任务
                            </Button>
                          )}
                      </Space>
                    </PermissionGate>
                  )}
                </Space>
              );
            },
          },
        ]}
        expandable={{
          expandedRowRender: (row) => (
            <pre
              style={{
                maxHeight: 320,
                overflow: "auto",
                whiteSpace: "pre-wrap",
                overflowWrap: "anywhere",
              }}
            >
              {JSON.stringify(row.result || {}, null, 2)}
            </pre>
          ),
        }}
      />
    </Space>
  );
}
