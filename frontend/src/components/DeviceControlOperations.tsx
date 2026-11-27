import { useState, type ReactNode } from "react";
import { Alert, Button, Collapse, Input, Modal, Space, Table } from "antd";
import PermissionGate from "./PermissionGate";
import type { AppRole } from "@/contexts/AuthContext";

export const controlWriters: AppRole[] = [
  "SUPER_ADMIN",
  "TENANT_ADMIN",
  "CUSTOMER_ADMIN",
];
const controlRepairers: AppRole[] = ["SUPER_ADMIN", "TENANT_ADMIN"];

const actions = {
  "cmd.send": { label: "发送命令" },
  "channel.reconcile.start": { label: "发起对账" },
  "channel.reconcile.review": {
    label: "复核对账",
    effect: "提交人工复核结果，配置平台将按所选结论推进对账。",
  },
  "channel.repair": {
    label: "修复通道",
    effect: "会变更设备控制通道，期间可能断开连接，并影响正在等待回执的任务。",
  },
  "policy.create": { label: "新建策略" },
  "policy.update": { label: "编辑策略" },
  "policy.delete": {
    label: "删除策略",
    effect: "策略及其规则将被删除，删除后无法从工作台恢复。",
  },
  "violation.acknowledge": { label: "确认违规" },
};
type Action = keyof typeof actions;
type Params = Record<string, unknown>;
type FormState = {
  action: Action;
  params: string;
  fixed: Params;
  command: string;
};

function containsCommand(value: unknown, command: string): boolean {
  if (typeof value === "string") return value.trim().toLowerCase() === command;
  return (
    value !== null &&
    typeof value === "object" &&
    Object.values(value).some((item) => containsCommand(item, command))
  );
}

const requiresBaseToken = new Set<Action>([
  "channel.reconcile.start",
  "channel.reconcile.review",
]);

export default function DeviceControlOperations({
  allowed,
  run,
  confirmAction,
  latest,
  baseToken,
}: {
  allowed: (action: string) => boolean;
  run: (action: string, params?: Params) => Promise<void>;
  confirmAction: (options: Parameters<typeof Modal.confirm>[0]) => void;
  latest: (action: string) => Record<string, any> | undefined;
  baseToken: string;
}) {
  const [form, setForm] = useState<FormState>();
  const [invalid, setInvalid] = useState("");
  const start = (action: Action, params: Params = {}) => {
    const initial: Params = {};
    if (requiresBaseToken.has(action)) {
      initial.expected_base_token = baseToken;
    }
    if (action === "channel.reconcile.review" || action === "channel.repair") {
      initial.operation_id = crypto.randomUUID().toLowerCase();
    }
    if (action === "channel.repair") {
      const metadata = latest("channel.diagnostics")?.metadataSha256;
      if (typeof metadata === "string" && metadata) {
        initial.expected_metadata_sha256 = metadata;
      }
    }
    setInvalid("");
    setForm({
      action,
      params: JSON.stringify(initial, null, 2),
      fixed: params,
      command: "",
    });
  };
  const actionButton = (action: Action, params: Params = {}) => {
    const missingBase = requiresBaseToken.has(action) && !baseToken;
    const button = (
      <Button
        disabled={!allowed(action) || missingBase}
        danger={["channel.repair", "policy.delete"].includes(action)}
        onClick={() => start(action, params)}
      >
        {actions[action].label}
      </Button>
    );
    return missingBase ? (
      <span title="请先读取配置和版本">{button}</span>
    ) : (
      button
    );
  };
  const readButton = (action: string, label: string) => (
    <Button disabled={!allowed(action)} onClick={() => void run(action)}>
      {label}
    </Button>
  );
  const findRows = (action: string, key: string): Params[] => {
    const result = latest(action);
    const rows: unknown = result?.items ?? result?.[key];
    return Array.isArray(rows)
      ? rows.filter(
          (value): value is Params => !!value && typeof value === "object",
        )
      : [];
  };
  const rowId = (row: Params, key: string) => {
    const value = row[key] ?? row.id;
    return typeof value === "string" || typeof value === "number"
      ? value
      : undefined;
  };
  const list = (
    data: Params[],
    idKey: string,
    rowActions: (row: Params) => ReactNode,
  ) => (
    <Table
      size="small"
      rowKey={(row) => String(rowId(row, idKey) ?? JSON.stringify(row))}
      dataSource={data}
      pagination={{ pageSize: 5, hideOnSinglePage: true }}
      columns={[
        { title: "编号", render: (_, row) => rowId(row, idKey) ?? "—" },
        {
          title: "名称 / 状态",
          render: (_, row) =>
            String(row.name ?? row.status ?? row.reason ?? "—"),
        },
        { title: "操作", render: (_, row) => rowActions(row) },
      ]}
      expandable={{
        expandedRowRender: (row) => (
          <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
            {JSON.stringify(row, null, 2)}
          </pre>
        ),
      }}
    />
  );
  const submit = () => {
    if (
      !form ||
      !allowed(form.action) ||
      (requiresBaseToken.has(form.action) && !baseToken)
    )
      return;
    let decoded: Params;
    try {
      const value: unknown = JSON.parse(form.params);
      if (!value || typeof value !== "object" || Array.isArray(value))
        throw new Error();
      decoded = value as Params;
    } catch {
      setInvalid("参数必须是 JSON 对象。");
      return;
    }
    let params = { ...decoded, ...form.fixed };
    if (form.action === "cmd.send") {
      const cmd = form.command.trim();
      const commandParams = { cmd, params: decoded };
      if (containsCommand(commandParams, "containment_approve")) {
        setInvalid("隔离审批请使用「人工审批」。");
        return;
      }
      if (containsCommand(commandParams, "containment_candidates")) {
        setInvalid("候选查询请使用「从设备拉取候选」。");
        return;
      }
      if (!cmd) {
        setInvalid("请输入命令。");
        return;
      }
      params = commandParams;
    }
    const { action } = form;
    const definition = actions[action];
    const effect = "effect" in definition ? definition.effect : undefined;
    const execute = () => {
      setForm(undefined);
      return run(
        action,
        action === "channel.reconcile.review"
          ? { ...params, human_confirmed: true }
          : params,
      );
    };
    if (effect) {
      const target = form.fixed.policy_id;
      confirmAction({
        title:
          target === undefined
            ? `确认${definition.label}？`
            : `确认${definition.label} #${target}？`,
        content: effect,
        okText: `确认${definition.label}`,
        cancelText: "返回",
        okButtonProps: { danger: true },
        onOk: execute,
      });
    } else {
      void execute();
    }
  };

  return (
    <>
      <Collapse
        items={[
          {
            key: "channel",
            label: "命令与通道",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                <Space wrap>
                  {readButton("channel.diagnostics", "通道诊断")}
                  <PermissionGate requiredRoles={controlWriters}>
                    <Space wrap>
                      {actionButton("cmd.send")}
                      {actionButton("channel.reconcile.start")}
                      {actionButton("channel.reconcile.review")}
                    </Space>
                  </PermissionGate>
                  <PermissionGate requiredRoles={controlRepairers}>
                    {actionButton("channel.repair")}
                  </PermissionGate>
                </Space>
                {latest("channel.diagnostics") && (
                  <pre
                    style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}
                  >
                    {JSON.stringify(latest("channel.diagnostics"), null, 2)}
                  </pre>
                )}
              </Space>
            ),
          },
          {
            key: "policies",
            label: "设备策略",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                <Space wrap>
                  {readButton("policy.list", "读取策略")}
                  <PermissionGate requiredRoles={controlWriters}>
                    {actionButton("policy.create")}
                  </PermissionGate>
                </Space>
                {list(
                  findRows("policy.list", "policies"),
                  "policyId",
                  (row) => {
                    const id = rowId(row, "policyId");
                    return id === undefined ? null : (
                      <PermissionGate requiredRoles={controlWriters}>
                        <Space wrap>
                          {actionButton("policy.update", { policy_id: id })}
                          {actionButton("policy.delete", { policy_id: id })}
                        </Space>
                      </PermissionGate>
                    );
                  },
                )}
              </Space>
            ),
          },
          {
            key: "violations",
            label: "违规",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                {readButton("violation.list", "读取违规")}
                {list(
                  findRows("violation.list", "violations"),
                  "violationId",
                  (row) => {
                    const id = rowId(row, "violationId");
                    return id === undefined ||
                      row.acknowledged === true ? null : (
                      <PermissionGate requiredRoles={controlWriters}>
                        {actionButton("violation.acknowledge", {
                          violation_id: id,
                        })}
                      </PermissionGate>
                    );
                  },
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={!!form}
        title={form && actions[form.action].label}
        okText="继续"
        cancelText="取消"
        onCancel={() => setForm(undefined)}
        onOk={submit}
      >
        {form && (
          <Space direction="vertical" style={{ width: "100%" }}>
            {"policy_id" in form.fixed && (
              <span>策略 #{String(form.fixed.policy_id)}</span>
            )}
            {"violation_id" in form.fixed && (
              <span>违规 #{String(form.fixed.violation_id)}</span>
            )}
            {form.action === "cmd.send" && (
              <Input
                aria-label="命令"
                placeholder="命令"
                value={form.command}
                onChange={(event) =>
                  setForm({ ...form, command: event.target.value })
                }
              />
            )}
            {form.action !== "policy.delete" && (
              <label style={{ display: "block" }}>
                动作参数 JSON
                <Input.TextArea
                  aria-label="动作参数 JSON"
                  value={form.params}
                  autoSize={{ minRows: 3, maxRows: 10 }}
                  onChange={(event) =>
                    setForm({ ...form, params: event.target.value })
                  }
                />
              </label>
            )}
            {invalid && <Alert type="error" message={invalid} />}
          </Space>
        )}
      </Modal>
    </>
  );
}
