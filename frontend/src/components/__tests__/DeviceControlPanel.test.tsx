import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DeviceControlPanel from "../DeviceControlPanel";
import {
  deviceControl,
  type ControlEvent,
  type ControlRecord,
} from "@/services/deviceControl";

const access = vi.hoisted(() => ({ role: "SUPER_ADMIN" }));
vi.mock("@/services/deviceControl", () => ({
  deviceControl: {
    readiness: vi.fn(),
    history: vi.fn(),
    submit: vi.fn(),
    events: vi.fn(),
    subscribeEvents: vi.fn(),
  },
}));
vi.mock("../PermissionGate", () => ({
  default: ({
    children,
    requiredRoles,
  }: {
    children: React.ReactNode;
    requiredRoles: string[];
  }) => (requiredRoles.includes(access.role) ? children : null),
}));

describe("device control execution feedback", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    access.role = "SUPER_ADMIN";
    vi.mocked(deviceControl.events).mockResolvedValue({
      items: [],
      nextCursor: "0",
      hasMore: false,
    });
    vi.mocked(deviceControl.subscribeEvents).mockResolvedValue();
    vi.mocked(deviceControl.readiness).mockResolvedValue({
      enabled: true,
      ready: true,
    });
    vi.mocked(deviceControl.submit).mockResolvedValue({
      requestId: "request",
      action: "capabilities",
      phase: "broker_acked",
    });
  });

  const capabilities = (...actions: string[]): ControlRecord => ({
    requestId: "cap-v11",
    action: "capabilities",
    phase: "completed",
    result: { result: { actions } },
  });
  const baseToken = "a".repeat(64);
  const configState = (
    action = "config.read",
    etag = 5,
    token = baseToken,
    text = "initial: true",
  ): ControlRecord => ({
    requestId: action,
    action,
    phase: "completed",
    result: {
      result: {
        etag: String(etag),
        baseToken: token,
        applied: { id: etag, yamlText: text },
      },
    },
  });
  const uuid4 =
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
  const showChannel = async () => {
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    fireEvent.click(await screen.findByText("命令与通道"));
  };
  const openChannelForm = async (label: string) => {
    const button = (
      await screen.findByText(label, { selector: "button span" })
    ).closest("button")!;
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button);
    return screen.findByRole("dialog", { name: label });
  };
  const confirmButton = async (label: string) =>
    (await screen.findByText(label, { selector: "button span" })).closest(
      "button",
    )!;

  it("blocks containment approval commands before submit", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("cmd.send"),
    ]);
    await showChannel();
    const dialog = await openChannelForm("发送命令");
    fireEvent.change(within(dialog).getByLabelText("命令"), {
      target: { value: " CONTAINMENT_APPROVE " },
    });
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(
      within(dialog).getByText("隔离审批请使用「人工审批」。"),
    ).toBeInTheDocument();
    expect(deviceControl.submit).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByLabelText("命令"), {
      target: { value: " CONTAINMENT_CANDIDATES " },
    });
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(
      within(dialog).getByText("候选查询请使用「从设备拉取候选」。"),
    ).toBeInTheDocument();
    expect(deviceControl.submit).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByLabelText("命令"), {
      target: { value: " diag " },
    });
    fireEvent.change(within(dialog).getByLabelText("动作参数 JSON"), {
      target: { value: '{"detail":true}' },
    });
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "cmd.send",
      { cmd: "diag", params: { detail: true } },
      expect.any(String),
    );
  });

  it("requires review confirmation and keeps repair hidden from customer admin", async () => {
    access.role = "CUSTOMER_ADMIN";
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("channel.reconcile.review", "channel.repair"),
      configState(),
    ]);
    await showChannel();
    expect(screen.queryByRole("button", { name: "修复通道" })).toBeNull();
    const dialog = await openChannelForm("复核对账");
    const input =
      within(dialog).getByLabelText<HTMLTextAreaElement>("动作参数 JSON");
    const params = JSON.parse(input.value);
    expect(params).toEqual({
      operation_id: expect.stringMatching(uuid4),
      expected_base_token: baseToken,
    });
    const override = "b".repeat(64);
    fireEvent.change(input, {
      target: {
        value: JSON.stringify({
          ...params,
          expected_base_token: override,
          human_confirmed: false,
        }),
      },
    });
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(deviceControl.submit).not.toHaveBeenCalled();
    fireEvent.click(await confirmButton("确认复核对账"));
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "channel.reconcile.review",
      { ...params, expected_base_token: override, human_confirmed: true },
      expect.any(String),
    );
  });

  it("warns about repair effects before submitting for a tenant admin", async () => {
    access.role = "TENANT_ADMIN";
    const metadata = "c".repeat(64);
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("channel.repair"),
      {
        requestId: "diag",
        action: "channel.diagnostics",
        phase: "completed",
        result: { result: { metadataSha256: metadata } },
      },
    ]);
    await showChannel();
    const dialog = await openChannelForm("修复通道");
    const params = JSON.parse(
      within(dialog).getByLabelText<HTMLTextAreaElement>("动作参数 JSON").value,
    );
    expect(params).toEqual({
      operation_id: expect.stringMatching(uuid4),
      expected_metadata_sha256: metadata,
    });
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(await screen.findByText(/期间可能断开连接/)).toBeInTheDocument();
    expect(deviceControl.submit).not.toHaveBeenCalled();
    fireEvent.click(await confirmButton("确认修复通道"));
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "channel.repair",
      params,
      expect.any(String),
    );
  });

  it("confirms irreversible policy deletion", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("policy.delete"),
      {
        requestId: "policy",
        action: "policy.list",
        phase: "completed",
        result: { result: { items: [{ id: 17, name: "设备策略 A" }] } },
      },
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    fireEvent.click(await screen.findByText("设备策略"));
    expect(await screen.findByText("设备策略 A")).toBeInTheDocument();
    const dialog = await openChannelForm("删除策略");
    fireEvent.click(
      within(dialog).getByText(/继\s*续/, { selector: "button span" }),
    );
    expect(
      await screen.findByText(/删除后无法从工作台恢复/),
    ).toBeInTheDocument();
    expect(deviceControl.submit).not.toHaveBeenCalled();
    fireEvent.click(await confirmButton("确认删除策略"));
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "policy.delete",
      { policy_id: 17 },
      expect.any(String),
    );
  });

  it("restricts task buttons to eligible phases and shows rollback as its own phase", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("task.retry", "task.cancel"),
      {
        requestId: "p",
        action: "config.push",
        phase: "accepted",
        result: { taskId: "running" },
      },
      {
        requestId: "r",
        action: "config.push",
        phase: "device_applied",
        result: { taskId: "rolled", currentEffect: "rolled_back" },
      },
      {
        requestId: "w",
        action: "config.push",
        phase: "device_rejected",
        result: {
          taskId: "waiting",
          result: { status: "device_pending_confirm" },
        },
      },
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    expect(await screen.findByText("已回滚")).toBeInTheDocument();
    const rolledBackRow = screen.getByText("已回滚").closest("tr")!;
    expect(within(rolledBackRow).getByText("设备执行成功")).toBeInTheDocument();
    expect(screen.getByText("设备待确认")).toBeInTheDocument();
    expect(
      screen.queryByText("复核重推", { selector: "button span" }),
    ).toBeNull();
    const retry = screen.getAllByText("重试任务", { selector: "button span" });
    expect(retry).toHaveLength(1);
    fireEvent.click(retry[0]);
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "task.retry",
      { task_id: "running" },
      expect.any(String),
    );
  });

  it("uses the current base token for ordinary retry and confirms configuration re-pushes", async () => {
    const task = (
      taskId: string,
      phase: string,
      details: Record<string, unknown> = {},
      action = "config.push",
    ): ControlRecord => ({
      requestId: taskId,
      action,
      phase,
      result: { taskId, result: details },
    });
    vi.mocked(deviceControl.history).mockResolvedValue([
      capabilities("task.retry"),
      configState(),
      task("running", "accepted"),
      task("pending", "device_rejected", { status: "device_pending_confirm" }),
      task(
        "manual",
        "accepted",
        { error: "control_reconciliation_required" },
        "task.retry",
      ),
      task("rejected", "device_rejected"),
      task("late", "timed_out"),
      task("command", "timed_out", {}, "cmd.send"),
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    const retry = await confirmButton("重试任务");
    fireEvent.click(retry);
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "task.retry",
      { task_id: "running", expected_base_token: baseToken },
      expect.any(String),
    );
    const reviewButtons = screen.getAllByText("复核重推", {
      selector: "button span",
    });
    expect(reviewButtons).toHaveLength(4);
    const pendingRow = screen.getByText("设备待确认").closest("tr")!;
    const review = within(pendingRow).getByText("复核重推").closest("button")!;
    await waitFor(() => expect(review).toBeEnabled());
    fireEvent.click(review);
    expect(
      await screen.findByText(/通过后创建新的配置版本和任务/),
    ).toBeInTheDocument();
    expect(deviceControl.submit).toHaveBeenCalledTimes(1);
    fireEvent.click(await confirmButton("确认重推"));
    expect(deviceControl.submit).toHaveBeenLastCalledWith(
      "customer-a",
      "SERIAL-A",
      "task.retry",
      { task_id: "pending", expected_base_token: baseToken },
      expect.any(String),
    );
  });

  it("refreshes configuration after reconcile start and review while preserving an unsaved edit baseline", async () => {
    const cap = capabilities(
      "channel.reconcile.start",
      "channel.reconcile.review",
      "config.draft",
      "task.retry",
    );
    vi.mocked(deviceControl.history).mockResolvedValue([cap]);
    await showChannel();
    await screen.findByText("控制连接已就绪");
    expect(await confirmButton("发起对账")).toBeDisabled();

    const refresh = () => fireEvent.click(screen.getByText("刷新结果"));
    const editor = await screen.findByLabelText("完整YAML配置");
    vi.mocked(deviceControl.history).mockResolvedValue([configState(), cap]);
    refresh();
    await waitFor(() => expect(editor).toHaveValue("initial: true"));

    const dialog = await openChannelForm("发起对账");
    expect(
      JSON.parse(
        within(dialog).getByLabelText<HTMLTextAreaElement>("动作参数 JSON")
          .value,
      ),
    ).toEqual({ expected_base_token: baseToken });
    const started = configState(
      "channel.reconcile.start",
      6,
      "b".repeat(64),
      "reconciled: true",
    );
    vi.mocked(deviceControl.history).mockResolvedValue([
      started,
      configState(),
      cap,
    ]);
    await act(async () =>
      fireEvent.click(
        within(dialog).getByText(/继\s*续/, { selector: "button span" }),
      ),
    );
    expect(deviceControl.submit).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      "channel.reconcile.start",
      { expected_base_token: baseToken },
      expect.any(String),
    );
    await waitFor(() => expect(editor).toHaveValue("reconciled: true"));
    expect(screen.getByText(/编辑基线：6/)).toBeInTheDocument();

    fireEvent.change(editor, { target: { value: "local: unsaved" } });
    const reviewed = configState(
      "channel.reconcile.review",
      7,
      "c".repeat(64),
      "reviewed: true",
    );
    vi.mocked(deviceControl.history).mockResolvedValue([
      reviewed,
      started,
      configState(),
      cap,
    ]);
    await act(async () => refresh());
    const reviewDialog = await openChannelForm("复核对账");
    expect(
      JSON.parse(
        within(reviewDialog).getByLabelText<HTMLTextAreaElement>(
          "动作参数 JSON",
        ).value,
      ),
    ).toMatchObject({ expected_base_token: "c".repeat(64) });
    expect(editor).toHaveValue("local: unsaved");
    expect(screen.getByText(/编辑基线：6/)).toBeInTheDocument();
    fireEvent.click(
      within(reviewDialog).getByText(/取\s*消/, { selector: "button span" }),
    );
    fireEvent.click(await confirmButton("保存草稿"));
    expect(deviceControl.submit).toHaveBeenLastCalledWith(
      "customer-a",
      "SERIAL-A",
      "config.draft",
      {
        yaml_text: "local: unsaved",
        expected_revision: 6,
        expected_base_token: "b".repeat(64),
      },
      expect.any(String),
    );
  });

  it("drains event notifications and refreshes control state without replacing dirty YAML", async () => {
    let notify!: () => void;
    vi.mocked(deviceControl.subscribeEvents).mockImplementation(
      async (_customer, _device, listener) => {
        notify = listener;
      },
    );
    const read = (requestId: string, text: string): ControlRecord => ({
      requestId,
      action: "config.read",
      phase: "completed",
      result: {
        result: {
          etag: "5",
          baseToken: "a".repeat(64),
          applied: { id: 5, yamlText: text },
        },
      },
    });
    const cap = capabilities("config.read", "lock.read", "violation.list");
    vi.mocked(deviceControl.history).mockResolvedValue([
      cap,
      read("old", "initial: true"),
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    const editor = await screen.findByLabelText("完整YAML配置");
    await waitFor(() => expect(editor).toHaveValue("initial: true"));
    fireEvent.change(editor, { target: { value: "unsaved: true" } });
    await waitFor(() =>
      expect(deviceControl.subscribeEvents).toHaveBeenCalled(),
    );
    const event = (
      eventType: ControlEvent["eventType"],
      n: number,
      data = {},
    ): ControlEvent => ({
      eventType,
      cursor: String(n),
      eventId: `e-${n}`,
      data,
      controllerId: "a",
      platformId: "b",
      deviceId: "d",
      devSerial: "SERIAL-A",
      occurredAt: "2026-11-26T10:00:00Z",
    });
    vi.mocked(deviceControl.events).mockResolvedValueOnce({
      nextCursor: "5",
      hasMore: false,
      items: [
        event("device.presence", 1, { online: true }),
        event("config.reported", 2),
        event("lock.changed", 3),
        event("task.terminal", 4),
        event("violation.created", 5),
      ],
    });
    vi.mocked(deviceControl.history).mockResolvedValue([
      read("new", "remote: changed"),
      cap,
    ]);
    await act(async () => notify());
    expect(await screen.findByText("设备在线")).toBeInTheDocument();
    expect(screen.getByText("收到新的设备违规")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        vi.mocked(deviceControl.submit).mock.calls.map((call) => call[2]),
      ).toEqual(["config.read", "lock.read", "violation.list"]),
    );
    expect(editor).toHaveValue("unsaved: true");
  });

  it("never presents Broker acceptance as device success", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      { requestId: "pending", action: "config.push", phase: "accepted" },
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    expect(
      await screen.findByText("任务已受理，等待设备回执"),
    ).toBeInTheDocument();
    expect(screen.queryByText("设备执行成功")).not.toBeInTheDocument();
    expect(deviceControl.history).toHaveBeenCalledWith(
      "customer-a",
      "SERIAL-A",
      expect.any(AbortSignal),
    );
  });

  it("preserves unsaved YAML when refreshing the same durable report", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      {
        requestId: "read-1",
        action: "config.read",
        phase: "completed",
        result: {
          result: {
            etag: "5",
            baseToken: "a".repeat(64),
            applied: { id: 5, yamlText: "initial: true" },
          },
        },
      },
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    const editor = await screen.findByLabelText("完整YAML配置");
    await waitFor(() => expect(editor).toHaveValue("initial: true"));
    fireEvent.change(editor, { target: { value: "local: unsaved" } });
    fireEvent.click(screen.getByText("刷新结果"));
    await waitFor(() => expect(deviceControl.history).toHaveBeenCalledTimes(2));
    expect(editor).toHaveValue("local: unsaved");
  });

  it("requires reading the registered action capabilities before writes", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    await screen.findByText("控制连接已就绪");
    expect(screen.getByText("保存草稿").closest("button")).toBeDisabled();
    expect(screen.getByText("下发草稿").closest("button")).toBeDisabled();
  });
  it("does not consume a broken result and accepts its repaired same request ID", async () => {
    vi.mocked(deviceControl.history).mockResolvedValue([
      {
        requestId: "cap-1",
        action: "capabilities",
        phase: "completed",
        resultDecodeError: "result_decode_error",
      },
    ]);
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    expect(
      await screen.findByText("结果读取异常，可查询原任务。编辑内容已保留。"),
    ).toBeInTheDocument();
    vi.mocked(deviceControl.history).mockResolvedValue([
      {
        requestId: "cap-1",
        action: "capabilities",
        phase: "completed",
        result: { result: { actions: ["device.status"] } },
      },
    ]);
    fireEvent.click(screen.getByText("刷新结果"));
    await waitFor(() =>
      expect(screen.getByText("设备状态").closest("button")).toBeEnabled(),
    );
    expect(
      screen.queryByText("结果读取异常，可查询原任务。编辑内容已保留。"),
    ).not.toBeInTheDocument();
  });

  it("preserves edits made while an earlier draft save is pending", async () => {
    const initial = [
      {
        requestId: "cap",
        action: "capabilities",
        phase: "completed",
        result: { result: { actions: ["config.draft", "config.push"] } },
      },
      {
        requestId: "read",
        action: "config.read",
        phase: "completed",
        result: {
          result: {
            etag: "5",
            baseToken: "a".repeat(64),
            draft: { id: 5, yamlText: "initial: true" },
          },
        },
      },
    ];
    vi.mocked(deviceControl.history).mockResolvedValue(initial);
    let finish!: (value: any) => void;
    vi.mocked(deviceControl.submit).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    render(<DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />);
    const editor = await screen.findByLabelText("完整YAML配置");
    await waitFor(() => expect(editor).toHaveValue("initial: true"));
    fireEvent.change(editor, { target: { value: "submitted: true" } });
    fireEvent.click(screen.getByText("保存草稿"));
    await waitFor(() =>
      expect(deviceControl.submit).toHaveBeenCalledWith(
        "customer-a",
        "SERIAL-A",
        "config.draft",
        {
          yaml_text: "submitted: true",
          expected_revision: 5,
          expected_base_token: "a".repeat(64),
        },
        expect.any(String),
      ),
    );
    fireEvent.change(editor, { target: { value: "newer: unsaved" } });
    vi.mocked(deviceControl.history).mockResolvedValue([
      {
        requestId: "saved",
        action: "config.draft",
        phase: "completed",
        result: {
          result: {
            id: 6,
            baseToken: "a".repeat(64),
            yamlText: "submitted: true",
          },
        },
      },
      ...initial,
    ]);
    finish({
      requestId: "saved",
      action: "config.draft",
      phase: "broker_acked",
    });
    await waitFor(() =>
      expect(screen.getByText(/草稿版本：6/)).toBeInTheDocument(),
    );
    expect(editor).toHaveValue("newer: unsaved");
    expect(screen.getByText("下发草稿").closest("button")).toBeDisabled();
    expect(
      screen.getByText("仍有未保存编辑；已保存的旧草稿不会包含这些输入。"),
    ).toBeInTheDocument();
  });

  it("ignores a late read from a different device context", async () => {
    let finishA!: (value: any) => void;
    vi.mocked(deviceControl.history).mockImplementation((customer) =>
      customer === "customer-a"
        ? new Promise((resolve) => {
            finishA = resolve;
          })
        : Promise.resolve([
            {
              requestId: "read-b",
              action: "config.read",
              phase: "completed",
              result: {
                result: {
                  etag: "9",
                  baseToken: "b".repeat(64),
                  applied: { id: 9, yamlText: "device: B" },
                },
              },
            },
          ]),
    );
    const view = render(
      <DeviceControlPanel customerId="customer-a" devSerial="SERIAL-A" />,
    );
    view.rerender(
      <DeviceControlPanel customerId="customer-b" devSerial="SERIAL-B" />,
    );
    const editor = screen.getByLabelText("完整YAML配置");
    await waitFor(() => expect(editor).toHaveValue("device: B"));
    finishA([
      {
        requestId: "read-a",
        action: "config.read",
        phase: "completed",
        result: {
          result: {
            etag: "1",
            baseToken: "a".repeat(64),
            applied: { id: 1, yamlText: "device: A" },
          },
        },
      },
    ]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(editor).toHaveValue("device: B");
  });
});
