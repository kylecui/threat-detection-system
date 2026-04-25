import { type ReactNode } from 'react';
import { usePermission } from '@/hooks/usePermission';
import type { AppRole } from '@/contexts/AuthContext';

interface PermissionGateProps {
  children: ReactNode;
  requiredRoles: AppRole[];
  fallback?: ReactNode;
}

export default function PermissionGate({ children, requiredRoles, fallback = null }: PermissionGateProps) {
  const { canAccess } = usePermission();

  if (!canAccess(requiredRoles)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
