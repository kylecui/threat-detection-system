import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import TableActions from '@/components/TableActions';
import TableActionButton from '@/components/TableActionButton';
import { TABLE_ACTION_COLUMN_WIDTH, tableActionColumnWidth } from '@/components/tableActionWidth';

describe('TableActions (INTERACTION-005)', () => {
  test('wraps actions in one non-wrapping, labelled group', () => {
    render(
      <TableActions label="Actions">
        <button type="button">Edit</button>
        <button type="button">Delete</button>
      </TableActions>,
    );
    const group = screen.getByRole('group', { name: 'Actions' });
    expect(group).toHaveClass('tds-table-actions');
    expect(group.querySelectorAll('button')).toHaveLength(2);
  });

  test('appends extra class names and keeps child order', () => {
    render(
      <TableActions label="Row actions" className="extra">
        <TableActionButton label="View" />
        <TableActionButton label="Edit" />
        <TableActionButton label="Delete" danger />
      </TableActions>,
    );
    const group = screen.getByRole('group', { name: 'Row actions' });
    expect(group).toHaveClass('tds-table-actions', 'extra');
    const names = Array.from(group.querySelectorAll('button')).map((button) => button.getAttribute('aria-label'));
    expect(names).toEqual(['View', 'Edit', 'Delete']);
  });

  test('icon buttons always expose an accessible name and tooltip title', () => {
    render(<TableActionButton label="Manage members" />);
    const button = screen.getByRole('button', { name: 'Manage members' });
    expect(button).toHaveAttribute('title', 'Manage members');
    expect(button).toHaveClass('tds-table-action-button');
  });
});

describe('action column width budget (INTERACTION-002)', () => {
  test('matches 40px buttons, 4px gaps and 24px cell padding (min 72px)', () => {
    expect(TABLE_ACTION_COLUMN_WIDTH).toEqual({ 1: 72, 2: 108, 3: 152, 4: 200 });
    for (const count of [1, 2, 3, 4] as const) {
      expect(tableActionColumnWidth(count)).toBeGreaterThanOrEqual(40 * count + 4 * (count - 1) + 24);
    }
  });
});
