import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import EmptyState from '@/components/EmptyState';

describe('EmptyState', () => {
  test('renders default description', () => {
    render(<EmptyState />);

    expect(screen.getByText('common.noData')).toBeInTheDocument();
  });

  test('renders custom description', () => {
    render(<EmptyState description="No threats found" />);

    expect(screen.getByText('No threats found')).toBeInTheDocument();
  });

  test('renders simple image variant', () => {
    const { container } = render(<EmptyState image="simple" />);

    expect(container.querySelector('.ant-empty-normal')).toBeInTheDocument();
  });
});
