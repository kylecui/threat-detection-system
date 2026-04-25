import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import ErrorBoundary from '@/components/ErrorBoundary';

let shouldThrow = false;

function Thrower() {
  if (shouldThrow) {
    throw new Error('Boom');
  }
  return <div>safe child</div>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    shouldThrow = false;
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  test('renders children normally', () => {
    render(
      <ErrorBoundary>
        <div>normal content</div>
      </ErrorBoundary>,
    );

    expect(screen.getByText('normal content')).toBeInTheDocument();
  });

  test('catches errors and shows fallback UI', () => {
    shouldThrow = true;

    render(
      <ErrorBoundary>
        <Thrower />
      </ErrorBoundary>,
    );

    expect(screen.getByText('errorBoundary.pageLoadError')).toBeInTheDocument();
    expect(screen.getByText('Boom')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'errorBoundary.reload' })).toBeInTheDocument();
  });

  test('retry button resets error state', async () => {
    const user = userEvent.setup();
    shouldThrow = true;

    render(
      <ErrorBoundary>
        <Thrower />
      </ErrorBoundary>,
    );

    shouldThrow = false;
    await user.click(screen.getByRole('button', { name: 'errorBoundary.reload' }));

    expect(screen.getByText('safe child')).toBeInTheDocument();
  });

  test('renders custom fallback prop when provided', () => {
    shouldThrow = true;

    render(
      <ErrorBoundary fallback={<div>custom fallback</div>}>
        <Thrower />
      </ErrorBoundary>,
    );

    expect(screen.getByText('custom fallback')).toBeInTheDocument();
  });
});
