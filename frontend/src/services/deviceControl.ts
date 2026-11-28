import api from "./api";

export function createUuidV4(): string {
  const crypto = globalThis.crypto;
  const nativeUuid = crypto?.randomUUID?.();
  if (nativeUuid !== undefined) return nativeUuid.toLowerCase();

  const bytes = new Uint8Array(16);
  if (typeof crypto?.getRandomValues === "function") {
    crypto.getRandomValues(bytes);
  } else {
    // Last resort for request and operation identifiers only.
    for (let index = 0; index < bytes.length; index++) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export interface ControlRecord {
  requestId: string;
  action: string;
  phase: string;
  createdAt?: string;
  resultVersion?: number;
  resultDecodeError?: string;
  result?: {
    result?: Record<string, any>;
    taskId?: string;
    phase?: string;
    action?: string;
    resultVersion?: number;
    currentEffect?: string;
    error?: string;
    devicePendingConfirm?: boolean;
    controlReconciliationRequired?: boolean;
  };
}

export interface ControlEvent {
  eventId: string;
  eventType:
    | "device.presence"
    | "config.reported"
    | "lock.changed"
    | "task.terminal"
    | "violation.created";
  controllerId: string;
  platformId: string;
  deviceId: string;
  devSerial: string;
  occurredAt: string;
  data: Record<string, unknown>;
  cursor: string;
}

export interface ControlEventPage {
  items: ControlEvent[];
  nextCursor: string;
  hasMore: boolean;
}

const path = (customer: string, serial: string) =>
  `/api/v1/device-control/${encodeURIComponent(customer)}/${encodeURIComponent(serial)}`;

export const deviceControl = {
  readiness: async (customer: string, serial: string, signal?: AbortSignal) =>
    (
      await api.get<{ enabled: boolean; ready: boolean }>(
        `${path(customer, serial)}/readiness`,
        { signal, suppressGlobalError: true },
      )
    ).data,
  history: async (customer: string, serial: string, signal?: AbortSignal) =>
    (
      await api.get<ControlRecord[]>(`${path(customer, serial)}/requests`, {
        signal,
        suppressGlobalError: true,
      })
    ).data,
  events: async (
    customer: string,
    serial: string,
    after?: string,
    signal?: AbortSignal,
  ) =>
    (
      await api.get<ControlEventPage>(`${path(customer, serial)}/events`, {
        params: { limit: 50, after },
        signal,
        skipScope: true,
        suppressGlobalError: true,
      })
    ).data,
  subscribeEvents: async (
    customer: string,
    serial: string,
    onNotification: () => void,
    signal: AbortSignal,
  ) => {
    const token = localStorage.getItem("token");
    if (!token) return;
    const response = await fetch(
      api.getUri({ url: `${path(customer, serial)}/events/stream` }),
      {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "text/event-stream",
        },
        signal,
        cache: "no-store",
      },
    );
    if (
      !response.ok ||
      !response.body ||
      !response.headers.get("Content-Type")?.startsWith("text/event-stream")
    ) {
      await response.body?.cancel();
      throw new Error("Event stream unavailable");
    }
    const reader = response.body
      .pipeThrough(new TextDecoderStream())
      .getReader();
    let buffer = "";
    try {
      while (!signal.aborted) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += value;
        let separator = /\r?\n\r?\n/.exec(buffer);
        while (separator) {
          const frame = buffer.slice(0, separator.index);
          buffer = buffer.slice(separator.index + separator[0].length);
          if (/^event:\s*control-event\s*$/m.test(frame)) onNotification();
          separator = /\r?\n\r?\n/.exec(buffer);
        }
        if (buffer.length > 8192)
          throw new Error("Invalid control event frame");
      }
    } finally {
      try {
        await reader.cancel();
      } finally {
        reader.releaseLock();
      }
    }
  },
  submit: async (
    customer: string,
    serial: string,
    action: string,
    params: Record<string, unknown>,
    idempotencyKey: string,
  ) =>
    (
      await api.post<ControlRecord>(`${path(customer, serial)}/requests`, {
        action,
        params,
        idempotencyKey,
      })
    ).data,
};
