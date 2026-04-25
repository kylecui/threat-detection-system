import { type ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { ThemeProvider, useTheme } from '@/contexts/ThemeContext';

function wrapper({ children }: { children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

describe('ThemeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    vi.restoreAllMocks();
  });

  test('defaults to system preference when no localStorage value exists', () => {
    const mockMatchMedia = vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({
      matches: query === '(prefers-color-scheme: dark)',
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.isDarkMode).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(mockMatchMedia).toHaveBeenCalledWith('(prefers-color-scheme: dark)');
  });

  test('toggle switches dark/light mode and persists to localStorage', () => {
    localStorage.setItem('tds-theme', 'light');

    const { result } = renderHook(() => useTheme(), { wrapper });

    expect(result.current.isDarkMode).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');

    act(() => {
      result.current.toggleTheme();
    });

    expect(result.current.isDarkMode).toBe(true);
    expect(localStorage.getItem('tds-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  test('throws when useTheme used outside provider', () => {
    expect(() => renderHook(() => useTheme())).toThrow('useTheme must be used within ThemeProvider');
  });
});
