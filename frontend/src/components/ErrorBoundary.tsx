// 集成说明：请在 App.tsx 的 ProLayout 内层包裹路由内容：<ErrorBoundary>{routeContent}</ErrorBoundary>
import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Button, Result } from 'antd';

interface Props {
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
    console.error('页面渲染错误:', error, errorInfo);
  }

  private handleReload = (): void => {
    this.setState({ hasError: false, error: null });
  };

  public render(): ReactNode {
    const { hasError, error } = this.state;
    const { children, fallback } = this.props;

    if (hasError) {
      if (fallback) {
        return fallback;
      }

      return (
        <Result
          status="error"
          title="页面加载出错"
          subTitle={error?.message || '发生未知错误，请稍后重试'}
          extra={
            <Button type="primary" onClick={this.handleReload}>
              重新加载
            </Button>
          }
        />
      );
    }

    return children;
  }
}

export default ErrorBoundary;
