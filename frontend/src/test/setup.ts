import '@testing-library/jest-dom';
import React from 'react';
import { vi } from 'vitest';

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), language: 'zh-CN' },
  }),
  withTranslation: () =>
    <P extends object>(Component: React.ComponentType<P & { t: (key: string) => string }>) =>
      (props: P) => React.createElement(Component, { ...props, t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));
