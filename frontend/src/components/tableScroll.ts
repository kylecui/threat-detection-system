import { useMemo } from "react";
import { Grid } from "antd";

/**
 * Minimal column shape needed to compute a scroll budget. Compatible with
 * antd `ColumnsType` and pro-components `ProColumns`.
 */
export interface ResponsiveWidthColumn {
  width?: number | string;
  responsive?: readonly string[];
}

/**
 * Sum of the numeric widths of the columns antd actually renders for the
 * given breakpoint state. antd shows a column with `responsive` only when at
 * least one of its breakpoints matches; columns without it always render.
 */
export function visibleColumnsWidth(
  columns: readonly ResponsiveWidthColumn[],
  screens: Partial<Record<string, boolean>>,
): number {
  return columns.reduce((total, column) => {
    const shown = !column.responsive || column.responsive.some((breakpoint) => screens[breakpoint]);
    return shown && typeof column.width === "number" ? total + column.width : total;
  }, 0);
}

/**
 * INTERACTION-004 finding: tables that hide columns with `responsive` must
 * not use a constant `scroll.x` equal to the full column sum. On narrow
 * screens the remaining columns would be stretched to that width and a
 * fixed action column could become wider than the visible table area.
 * This returns the width of the columns rendered at the current breakpoint.
 */
export function useResponsiveScrollX(columns: readonly ResponsiveWidthColumn[]): number {
  const screens = Grid.useBreakpoint();
  return useMemo(() => visibleColumnsWidth(columns, screens), [columns, screens]);
}
