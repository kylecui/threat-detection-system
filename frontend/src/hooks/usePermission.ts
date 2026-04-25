import { useMemo } from 'react';
import { useAuth, type AppRole } from '@/contexts/AuthContext';

interface PermissionCheck {
  hasRole: (role: AppRole) => boolean;
  hasAnyRole: (roles: AppRole[]) => boolean;
  hasAllRoles: (roles: AppRole[]) => boolean;
  canAccess: (requiredRoles: AppRole[]) => boolean;
  isSuperAdmin: boolean;
  isTenantAdmin: boolean;
  isCustomerUser: boolean;
  isAdminRole: boolean;
}

export function usePermission(): PermissionCheck {
  const { user, isSuperAdmin, isTenantAdmin, isCustomerUser } = useAuth();

  return useMemo(() => {
    const roles = user?.roles ?? [];

    const hasRole = (role: AppRole) => roles.includes(role);

    const hasAnyRole = (required: AppRole[]) =>
      required.some((r) => roles.includes(r));

    const hasAllRoles = (required: AppRole[]) =>
      required.every((r) => roles.includes(r));

    const canAccess = (requiredRoles: AppRole[]) => {
      if (requiredRoles.length === 0) return true;
      if (isSuperAdmin) return true;
      return hasAnyRole(requiredRoles);
    };

    return {
      hasRole,
      hasAnyRole,
      hasAllRoles,
      canAccess,
      isSuperAdmin,
      isTenantAdmin,
      isCustomerUser,
      isAdminRole: isSuperAdmin || isTenantAdmin,
    };
  }, [user, isSuperAdmin, isTenantAdmin, isCustomerUser]);
}
