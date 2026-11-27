import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import NetworkIdentifier, { formatNetworkIdentifier } from '@/components/NetworkIdentifier';

describe('NetworkIdentifier', () => {
  test('normalizes valid MAC addresses for display only', () => {
    expect(formatNetworkIdentifier('10-e9-53-d4-67-a0', 'mac')).toBe('10:E9:53:D4:67:A0');
    render(<NetworkIdentifier value="10:e9:53:d4:67:a0" kind="mac" />);
    expect(screen.getByText('10:E9:53:D4:67:A0')).toHaveClass('tds-network-identifier--mac');
  });

  test('keeps IP values unchanged and supplies a safe fallback', () => {
    const { rerender } = render(<NetworkIdentifier value="192.168.2.29" kind="ip" />);
    expect(screen.getByText('192.168.2.29')).toBeInTheDocument();
    rerender(<NetworkIdentifier value={null} kind="serial" />);
    expect(screen.getByText('-')).toBeInTheDocument();
  });
});
