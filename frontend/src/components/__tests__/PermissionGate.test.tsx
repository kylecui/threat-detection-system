import { type ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, test } from 'vitest';
import { AuthProvider } from '@/contexts/AuthContext';
import PermissionGate from '@/components/PermissionGate';

function withAuth(children: ReactNode) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('PermissionGate', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('shows children when user has required role', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 1,
        username: 'tenant',
        roles: ['TENANT_ADMIN'],
      }),
    );

    render(
      withAuth(
        <PermissionGate requiredRoles={['TENANT_ADMIN']}>
          <div>allowed content</div>
        </PermissionGate>,
      ),
    );

    expect(screen.getByText('allowed content')).toBeInTheDocument();
  });

  test('hides children when user lacks required role', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 2,
        username: 'customer',
        roles: ['CUSTOMER_USER'],
      }),
    );

    render(
      withAuth(
        <PermissionGate requiredRoles={['TENANT_ADMIN']}>
          <div>forbidden content</div>
        </PermissionGate>,
      ),
    );

    expect(screen.queryByText('forbidden content')).not.toBeInTheDocument();
  });

  test('renders fallback when provided and access denied', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 3,
        username: 'customer',
        roles: ['CUSTOMER_USER'],
      }),
    );

    render(
      withAuth(
        <PermissionGate requiredRoles={['SUPER_ADMIN']} fallback={<div>fallback content</div>}>
          <div>hidden content</div>
        </PermissionGate>,
      ),
    );

    expect(screen.getByText('fallback content')).toBeInTheDocument();
    expect(screen.queryByText('hidden content')).not.toBeInTheDocument();
  });
});
