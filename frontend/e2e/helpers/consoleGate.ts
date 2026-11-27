import type { ConsoleMessage, Page } from "@playwright/test";

/**
 * Console / page error gate shared by specs (INTERACTION-003, kept strict by
 * INTERACTION-012).
 *
 * Every `console.error` and uncaught page error fails the test, except the
 * reviewed entries below. React StrictMode warnings (e.g. `findDOMNode is
 * deprecated`) are deliberately NOT allowed: INTERACTION-012 removed their
 * source (ProTable's built-in density item) instead of hiding them. Adding an
 * entry requires an exact message, a reason and an issue reference.
 */
export const CONSOLE_ALLOWLIST: ReadonlyArray<{ pattern: RegExp; reason: string }> = [
  {
    pattern: /^Warning: \[antd: [^\]]+\]/,
    reason: "antd component deprecation notices printed by the development build (INTERACTION-003)",
  },
];

export function isAllowedConsoleError(text: string): boolean {
  return CONSOLE_ALLOWLIST.some(({ pattern }) => pattern.test(text));
}

export function collectErrors(page: Page) {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  page.on("console", (message: ConsoleMessage) => {
    if (message.type() !== "error") return;
    const text = message.text();
    if (!isAllowedConsoleError(text)) consoleErrors.push(text);
  });
  return { pageErrors, consoleErrors };
}
