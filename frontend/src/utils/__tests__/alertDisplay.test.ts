import { describe, expect, it } from "vitest";
import { formatAlertOwner } from "../alertDisplay";

describe("formatAlertOwner", () => {
  it("returns a trimmed owner username", () => {
    expect(formatAlertOwner("  analyst01  ")).toBe("analyst01");
  });

  it("uses the fallback for missing or unexpected legacy values", () => {
    expect(formatAlertOwner(undefined)).toBe("-");
    expect(formatAlertOwner("   ", "未分配")).toBe("未分配");
    expect(formatAlertOwner({ username: "analyst01" })).toBe("-");
  });
});
