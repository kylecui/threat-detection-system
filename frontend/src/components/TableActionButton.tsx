import { forwardRef, type ComponentRef } from 'react';
import { Button } from 'antd';
import type { ButtonProps } from 'antd';

type TableActionButtonProps = Omit<ButtonProps, 'aria-label'> & {
  label: string;
};

/**
 * Consistent icon action for dense data tables.
 *
 * The visual icon stays compact while the 40px hit target, delayed tooltip,
 * keyboard name and optical offset remain identical across every table.
 */
const TableActionButton = forwardRef<
  ComponentRef<typeof Button>,
  TableActionButtonProps
>(function TableActionButton(
  { className, label, type = 'text', ...buttonProps },
  ref,
) {
  const classes = ['tds-table-action-button', className].filter(Boolean).join(' ');

  return (
    <Button
      ref={ref}
      {...buttonProps}
      type={type}
      className={classes}
      aria-label={label}
      title={label}
    />
  );
});

export default TableActionButton;
