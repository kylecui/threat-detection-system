import { describe, expect, it } from "vitest";
import { controlPhases, controlResult } from "../controlResults";
import type { ControlRecord } from "../deviceControl";

const task = (
  phase: string,
  result?: ControlRecord["result"],
): ControlRecord => ({
  requestId: "request",
  action: "config.push",
  phase,
  result,
});

describe("v1.1 control result phases", () => {
  it.each([
    { currentEffect: "rolled_back" },
    { result: { currentEffect: "rolled_back" } },
    { result: { followup: { status: "rolled_back" } } },
  ])("preserves historical success with rollback in %j", (result) => {
    expect(controlPhases(task("device_applied", result))).toEqual([
      "device_applied",
      "rolled_back",
    ]);
  });

  it.each([
    { status: "device_pending_confirm" },
    { deviceStatus: "pending_confirm" },
    { error: "pending_confirmation" },
  ])("keeps the original phase with pending confirmation in %j", (result) => {
    expect(controlPhases(task("device_rejected", { result }))).toEqual([
      "device_rejected",
      "device_pending_confirm",
    ]);
  });

  it.each([
    { error: "control_reconciliation_required" },
    { result: { error: "control_reconciliation_required" } },
  ])("adds required reconciliation from %j", (result) => {
    expect(controlPhases(task("accepted", result))).toEqual([
      "accepted",
      "control_reconciliation_required",
    ]);
  });

  it("deduplicates the status when the phase and payload agree", () => {
    expect(
      controlPhases(
        task("device_pending_confirm", {
          result: { status: "device_pending_confirm" },
        }),
      ),
    ).toEqual(["device_pending_confirm"]);
  });

  it.each(["channel.reconcile.start", "channel.reconcile.review"])(
    "validates %s as a configuration snapshot before it can replace state",
    (action) => {
      const value = {
        etag: "7",
        baseToken: "a".repeat(64),
        applied: { yamlText: "valid: true" },
      };
      expect(
        controlResult({ ...task("completed", { result: value }), action }),
      ).toEqual(value);
      expect(
        controlResult({
          ...task("completed", { result: { ...value, baseToken: "wrong" } }),
          action,
        }),
      ).toBeUndefined();
    },
  );
});
