import type { Locator, Page } from "@playwright/test";

/**
 * INTERACTION-013: locate ProTable query forms through the app-owned hook
 * (`search.className = "tds-table-query"`, src/components/proTableConfig.ts)
 * and fields through their accessible names. Never use Pro Components'
 * internal wrapper / QueryFilter class names in specs.
 */
export const TABLE_QUERY_SELECTOR = ".tds-content .tds-table-query";

/** The visible query form (inactive tabs keep their hidden ProTables mounted). */
export function tableQuery(page: Page): Locator {
  return page.locator(`${TABLE_QUERY_SELECTOR}:visible`).first();
}

/** One query field: its input (by accessible name), label and control box. */
export function tableQueryField(page: Page, name: string | RegExp) {
  const input = tableQuery(page).getByRole("textbox", { name }).first();
  const item = input.locator("xpath=ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' ant-form-item ')][1]");
  return {
    input,
    item,
    label: item.locator(".ant-form-item-label").first(),
    control: item.locator(".ant-form-item-control-input").first(),
  };
}

type Box = { left: number; right: number; top: number; bottom: number };

export interface QueryGeometry {
  form: { x: number; y: number; width: number; height: number } | null;
  fields: Array<{ label: string; labelCenter: number; controlCenter: number; labelBox: Box; controlBox: Box }>;
  buttons: Array<{ text: string; box: Box }>;
}

/** Geometry of every visible field and button in the query form. */
export async function queryGeometry(query: Locator): Promise<QueryGeometry> {
  return query.evaluate((root) => {
    const box = (element: Element) => {
      const rect = element.getBoundingClientRect();
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
    };
    const visible = (element: Element) => {
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
    };
    const formRect = root.getBoundingClientRect();
    const fields = Array.from(root.querySelectorAll(".ant-form-item"))
      .filter((item) => visible(item) && item.querySelector(".ant-form-item-label label"))
      .map((item) => {
        const label = item.querySelector(".ant-form-item-label label") as Element;
        const control = item.querySelector(".ant-form-item-control-input") as Element;
        const labelBox = box(label);
        const controlBox = box(control);
        return {
          label: (label.textContent ?? "").trim(),
          labelCenter: (labelBox.top + labelBox.bottom) / 2,
          controlCenter: (controlBox.top + controlBox.bottom) / 2,
          labelBox,
          controlBox,
        };
      });
    // Reset / Query buttons and the Expand/Collapse toggle (an <a>).
    const buttons = Array.from(root.querySelectorAll("button, a"))
      .filter(visible)
      .map((button) => ({ text: (button.textContent ?? "").trim(), box: box(button) }));
    return { form: { x: formRect.left, y: formRect.top, width: formRect.width, height: formRect.height }, fields, buttons };
  });
}
