import { describe, expect, test } from 'vitest';
import { visibleColumnsWidth } from '@/components/tableScroll';

describe('visibleColumnsWidth (INTERACTION-004 finding)', () => {
  const columns = [
    { width: 100 },
    { width: 200, responsive: ['xl'] },
    { width: 50, responsive: ['md', 'lg'] },
    { width: '20%' },
    { width: 120 },
  ];

  test('counts only the columns antd renders at the current breakpoint', () => {
    expect(visibleColumnsWidth(columns, { xs: true })).toBe(220);
    expect(visibleColumnsWidth(columns, { xs: true, sm: true, md: true })).toBe(270);
    expect(visibleColumnsWidth(columns, { xs: true, sm: true, md: true, lg: true, xl: true })).toBe(470);
  });

  test('ignores non-numeric widths', () => {
    expect(visibleColumnsWidth([{ width: '30%' }], {})).toBe(0);
  });
});
