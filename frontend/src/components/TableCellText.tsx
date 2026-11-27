import { useCallback, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { Tooltip } from 'antd';

export interface TableCellTextProps {
  /** Value rendered on a single line. Empty values render the fallback. */
  value?: ReactNode;
  /** Full text for the tooltip; defaults to `value` when it is a string/number. */
  fullText?: ReactNode;
  fallback?: string;
  className?: string;
  style?: CSSProperties;
}

const isEmpty = (value: ReactNode) =>
  value === null ||
  value === undefined ||
  value === false ||
  (typeof value === 'string' && value.trim() === '');

// eslint-disable-next-line react-refresh/only-export-components
export const isTextOverflowing = (element: HTMLElement | null): boolean =>
  !!element && element.scrollWidth > element.clientWidth + 1;

/**
 * INTERACTION-001: single-line, ellipsised table cell text.
 *
 * Use only for long, non-critical text (titles, descriptions, names, URLs,
 * model paths, e-mails). The tooltip with the full value opens only when the
 * text is actually truncated, so short values never get a redundant popup.
 */
const TableCellText = ({
  value,
  fullText,
  fallback = '-',
  className,
  style,
}: TableCellTextProps) => {
  const ref = useRef<HTMLSpanElement>(null);
  const [open, setOpen] = useState(false);
  const empty = isEmpty(value);
  const display = empty ? fallback : value;
  const tooltip =
    fullText ?? (typeof value === 'string' || typeof value === 'number' ? value : undefined);

  const handleOpenChange = useCallback((next: boolean) => {
    setOpen(next && isTextOverflowing(ref.current));
  }, []);

  const classes = ['tds-table-cell-text', className].filter(Boolean).join(' ');

  const content = (
    <span ref={ref} className={classes} style={style} data-full-text={typeof tooltip === 'string' ? tooltip : undefined}>
      {display}
    </span>
  );

  if (empty || isEmpty(tooltip ?? null)) {
    return content;
  }

  return (
    <Tooltip title={tooltip} open={open} onOpenChange={handleOpenChange}>
      {content}
    </Tooltip>
  );
};

export default TableCellText;
