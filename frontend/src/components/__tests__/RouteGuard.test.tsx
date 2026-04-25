import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, test } from 'vitest';
import { AuthProvider } from '@/contexts/AuthContext';
import RouteGuard from '@/components/RouteGuard';

describe('RouteGuard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('redirects to /login when not authenticated', () => {
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/protected"
              element={
                <RouteGuard>
                  <div>protected content</div>
                </RouteGuard>
              }
            />
            <Route path="/login" element={<div>login page</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  test('renders children when authenticated', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 1,
        username: 'u1',
        roles: ['CUSTOMER_USER'],
      }),
    );

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/protected"
              element={
                <RouteGuard>
                  <div>protected content</div>
                </RouteGuard>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.getByText('protected content')).toBeInTheDocument();
  });
});
