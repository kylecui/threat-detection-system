/**
 * INTERACTION-002: width budget for table action columns built from
 * 40px `TableActionButton`s.
 *
 * Derivation: 40 × buttons + 4 × (buttons - 1) + 24 (cell inline padding),
 * then two project adjustments:
 * - one button would be 64px, but the column never goes below 72px so the
 *   "Actions" / "操作" header always fits on one line;
 * - four buttons (196px) keep the established 200px budget (already used by
 *   the alert center), which leaves room for the focus ring.
 */
export const TABLE_ACTION_BUTTON_SIZE = 40;
export const TABLE_ACTION_GAP = 4;
export const TABLE_CELL_INLINE_PADDING = 24;

export const TABLE_ACTION_COLUMN_WIDTH = {
  1: 72,
  2: 108,
  3: 152,
  4: 200,
} as const;

export type TableActionCount = keyof typeof TABLE_ACTION_COLUMN_WIDTH;

export const tableActionColumnWidth = (buttons: TableActionCount): number =>
  TABLE_ACTION_COLUMN_WIDTH[buttons];
