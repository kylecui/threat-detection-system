// 集成说明：请在 App.tsx 的 ProLayout 内层包裹路由内容：<ErrorBoundary>{routeContent}</ErrorBoundary>
import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Button, Result } from 'antd';
import { withTranslation, type WithTranslation } from 'react-i18next';

interface Props extends WithTranslation {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
    };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('Page render error:', error, errorInfo);
  }

  private handleReload = (): void => {
    this.setState({ hasError: false, error: null });
  };

  public render(): ReactNode {
    const { hasError, error } = this.state;
    const { children, fallback, t } = this.props;

    if (hasError) {
      if (fallback) {
        return fallback;
      }

      return (
        <Result
          status="error"
          title={t('errorBoundary.pageLoadError')}
          subTitle={error?.message || t('errorBoundary.unknownError')}
          extra={
            <Button type="primary" onClick={this.handleReload}>
              {t('errorBoundary.reload')}
            </Button>
          }
        />
      );
    }

    return children;
  }
}

export default withTranslation()(ErrorBoundary);
