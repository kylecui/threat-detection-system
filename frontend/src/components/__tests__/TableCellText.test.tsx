import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import TableCellText, { isTextOverflowing } from '@/components/TableCellText';

describe('TableCellText', () => {
  test('renders the value on a single ellipsis line and keeps the full text', () => {
    render(<TableCellText value="https://feeds.example.com/very/long/path" />);
    const cell = screen.getByText('https://feeds.example.com/very/long/path');
    expect(cell).toHaveClass('tds-table-cell-text');
    expect(cell).toHaveAttribute('data-full-text', 'https://feeds.example.com/very/long/path');
  });

  test('renders a fallback for empty values', () => {
    const { rerender } = render(<TableCellText value={null} />);
    expect(screen.getByText('-')).toBeInTheDocument();
    rerender(<TableCellText value="   " fallback="—" />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  test('merges extra class names', () => {
    render(<TableCellText value="/models/tier1.onnx" className="tds-operate-code" />);
    expect(screen.getByText('/models/tier1.onnx')).toHaveClass('tds-table-cell-text', 'tds-operate-code');
  });

  test('detects overflow only when content is wider than the box', () => {
    const element = document.createElement('span');
    Object.defineProperty(element, 'clientWidth', { configurable: true, value: 100 });
    Object.defineProperty(element, 'scrollWidth', { configurable: true, value: 100 });
    expect(isTextOverflowing(element)).toBe(false);
    Object.defineProperty(element, 'scrollWidth', { configurable: true, value: 180 });
    expect(isTextOverflowing(element)).toBe(true);
    expect(isTextOverflowing(null)).toBe(false);
  });
});
