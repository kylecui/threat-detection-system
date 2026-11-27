import type { ReactNode } from 'react';

export interface TableActionsProps {
  children: ReactNode;
  className?: string;
  /** Optional accessible name for the action group. */
  label?: string;
}

/**
 * INTERACTION-001: centred, non-wrapping container for table row actions.
 *
 * Replaces `<Space>` / `<Space wrap>` in action columns so buttons never
 * break onto a second line and never change the row height. Pair with
 * `TableActionButton` to keep the 40px hit target and accessible names.
 */
const TableActions = ({ children, className, label }: TableActionsProps) => {
  const classes = ['tds-table-actions', className].filter(Boolean).join(' ');
  return (
    <div className={classes} role="group" aria-label={label}>
      {children}
    </div>
  );
};

export default TableActions;
