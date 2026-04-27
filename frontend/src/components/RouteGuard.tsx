import { Navigate, useLocation } from 'react-router-dom';
import { useAuth, type AppRole } from '@/contexts/AuthContext';

interface RouteGuardProps {
  children: React.ReactNode;
  requiredRoles?: AppRole[];
}

export default function RouteGuard({ children, requiredRoles }: RouteGuardProps) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRoles && requiredRoles.length > 0) {
    const userRoles = user?.roles ?? [];
    const isSuperAdmin = userRoles.includes('SUPER_ADMIN');
    if (!isSuperAdmin && !requiredRoles.some((r) => userRoles.includes(r))) {
      return <Navigate to="/overview" replace />;
    }
  }

  return <>{children}</>;
}
