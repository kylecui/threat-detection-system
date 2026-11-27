import type { HTMLAttributes } from 'react';

export type NetworkIdentifierKind = 'mac' | 'ip' | 'port' | 'serial' | 'generic';

export interface NetworkIdentifierProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  value?: string | number | null;
  kind?: NetworkIdentifierKind;
  fallback?: string;
}

const MAC_PATTERN = /^([0-9a-f]{2}[:-]){5}[0-9a-f]{2}$/i;

// eslint-disable-next-line react-refresh/only-export-components
export const formatNetworkIdentifier = (
  value: string | number,
  kind: NetworkIdentifierKind,
): string => {
  const rawValue = String(value).trim();
  if (kind === 'mac' && MAC_PATTERN.test(rawValue)) {
    return rawValue.replace(/-/g, ':').toUpperCase();
  }
  return rawValue;
};

const NetworkIdentifier = ({
  value,
  kind = 'generic',
  fallback = '-',
  className = '',
  title,
  ...props
}: NetworkIdentifierProps) => {
  const hasValue = value !== null && value !== undefined && String(value).trim() !== '';
  const displayValue = hasValue ? formatNetworkIdentifier(value, kind) : fallback;

  return (
    <span
      className={`tds-network-identifier tds-network-identifier--${kind} ${className}`.trim()}
      title={title ?? displayValue}
      {...props}
    >
      {displayValue}
    </span>
  );
};

export default NetworkIdentifier;
