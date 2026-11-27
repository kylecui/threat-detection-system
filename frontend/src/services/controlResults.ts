import type { ControlRecord } from "./deviceControl";
const object = (value: unknown): value is Record<string, any> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

export function isConfigStateAction(action: string): boolean {
  return [
    "config.read",
    "channel.reconcile.start",
    "channel.reconcile.review",
  ].includes(action);
}

export function controlResult(
  row: ControlRecord,
): Record<string, any> | undefined {
  if (row.resultDecodeError || !object(row.result)) return undefined;
  const value: unknown = row.result.result;
  if (
    Array.isArray(value) &&
    ["policy.list", "violation.list"].includes(row.action)
  ) {
    return { items: value.filter(object) };
  }
  if (!object(value)) {
    return row.action === "policy.delete" &&
      row.phase === "completed" &&
      value == null
      ? {}
      : undefined;
  }
  const result = value;
  if (
    row.action === "capabilities" &&
    (!Array.isArray(result.actions) ||
      !result.actions.every((a: unknown) => typeof a === "string"))
  )
    return undefined;
  if (isConfigStateAction(row.action) && row.phase === "completed") {
    if (
      !/^\d+$/.test(String(result.etag)) ||
      !/^[0-9a-f]{64}$/.test(result.baseToken || "")
    )
      return undefined;
    for (const key of ["observed", "desired", "applied", "draft"]) {
      const doc = result[key];
      if (doc != null && (!object(doc) || typeof doc.yamlText !== "string"))
        return undefined;
    }
  }
  if (
    ["config.draft", "config.restore"].includes(row.action) &&
    (!Number.isSafeInteger(result.id) ||
      result.id <= 0 ||
      !/^[0-9a-f]{64}$/.test(result.baseToken || ""))
  )
    return undefined;
  if (
    row.action === "config.history" &&
    (!Array.isArray(result.items) ||
      !result.items.every(
        (doc: unknown) =>
          object(doc) &&
          Number.isSafeInteger(doc.id) &&
          typeof doc.yamlText === "string",
      ))
  )
    return undefined;
  if (row.action === "candidates.pull" && row.phase === "device_applied") {
    if (
      typeof row.result.taskId !== "string" ||
      !row.result.taskId ||
      !object(result.result) ||
      !Array.isArray(result.result.candidates) ||
      !result.result.candidates.every(
        (candidate: unknown) =>
          object(candidate) &&
          Number.isSafeInteger(candidate.id ?? candidate.candidateId),
      )
    )
      return undefined;
  }
  return result;
}

export function controlPhases(row: ControlRecord): string[] {
  const details = controlResult(row);
  const phases = new Set([row.phase]);
  if (
    row.result?.currentEffect === "rolled_back" ||
    details?.currentEffect === "rolled_back" ||
    details?.followup?.status === "rolled_back"
  )
    phases.add("rolled_back");
  if (
    row.result?.controlReconciliationRequired === true ||
    details?.controlReconciliationRequired === true ||
    details?.error === "control_reconciliation_required" ||
    row.result?.error === "control_reconciliation_required"
  )
    phases.add("control_reconciliation_required");
  if (
    row.result?.devicePendingConfirm === true ||
    details?.devicePendingConfirm === true ||
    details?.status === "device_pending_confirm" ||
    details?.deviceStatus === "pending_confirm" ||
    details?.error === "pending_confirmation"
  )
    phases.add("device_pending_confirm");
  return [...phases];
}
