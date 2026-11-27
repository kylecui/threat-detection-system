import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Link,
  useNavigate,
  useLocation,
} from "react-router-dom";
import {
  lazy,
  Suspense,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import {
  Avatar,
  Button,
  ConfigProvider,
  Drawer,
  Dropdown,
  Grid,
  Layout,
  Menu,
  Spin,
  Tooltip,
  Typography,
  type MenuProps,
} from "antd";
import zhCN from "antd/locale/zh_CN";
import enUS from "antd/locale/en_US";
import {
  ApiOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  BellOutlined,
  CheckOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  DownOutlined,
  ExperimentOutlined,
  GlobalOutlined,
  LogoutOutlined,
  MenuOutlined,
  MoonOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { AuthProvider, useAuth, type AppRole } from "./contexts/AuthContext";
import { ScopeProvider, useScope } from "./contexts/ScopeContext";
import { useTheme } from "./contexts/ThemeContext";
import { createAppTheme } from "./theme/appTheme";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import RouteGuard from "./components/RouteGuard";
import ErrorBoundary from "./components/ErrorBoundary";
import ScopeSelector from "./components/ScopeSelector";

const Overview = lazy(() => import("./pages/Overview"));
const ThreatList = lazy(() => import("./pages/ThreatList"));
const AlertCenter = lazy(() => import("./pages/AlertCenter"));
const CustomersAndDevices = lazy(() => import("./pages/CustomersAndDevices"));
const DeviceControlDevices = lazy(() => import("./pages/CustomersAndDevices").then(module => ({ default: module.DevicesTab })));
const ThreatIntel = lazy(() => import("./pages/ThreatIntel"));
const MlDetection = lazy(() => import("./pages/MlDetection"));
const PipelineHealth = lazy(() => import("./pages/PipelineHealth"));
const LoginPage = lazy(() => import("./pages/Login"));
const NotFound = lazy(() => import("./pages/NotFound"));
const TenantMgmt = lazy(() => import("./pages/TenantMgmt"));
const UserMgmt = lazy(() => import("./pages/UserMgmt"));
const GeneralConfig = lazy(() => import("./pages/Configuration/GeneralConfig"));
const NotificationConfig = lazy(
  () => import("./pages/Configuration/NotificationConfig"),
);
const IntegrationConfig = lazy(
  () => import("./pages/Configuration/IntegrationConfig"),
);
const AIConfig = lazy(() => import("./pages/Configuration/AIConfig"));
const PluginConfig = lazy(() => import("./pages/Configuration/PluginConfig"));

const { Header, Sider, Content } = Layout;
const { Text } = Typography;
const { useBreakpoint } = Grid;

interface MenuRoute {
  path: string;
  name: string;
  icon?: ReactNode;
  requiredRoles?: AppRole[];
  routes?: MenuRoute[];
}

type WorkspaceId = "monitoring" | "operations" | "governance" | "configuration";

interface WorkspaceDefinition {
  id: WorkspaceId;
  name: string;
  description: string;
  accent: string;
  icon: ReactNode;
  roots: string[];
  defaultPath: string;
  matches: (pathname: string) => boolean;
}

const getAllMenuRoutes = (t: TFunction): MenuRoute[] => [
  { path: "/overview", name: t("nav.overview"), icon: <DashboardOutlined /> },
  {
    path: "/investigate",
    name: t("nav.investigate"),
    icon: <SearchOutlined />,
    routes: [
      {
        path: "/investigate/alerts",
        name: t("nav.alerts"),
        icon: <BellOutlined />,
      },
      {
        path: "/investigate/threats",
        name: t("nav.threats"),
        icon: <WarningOutlined />,
      },
      {
        path: "/investigate/intel",
        name: t("nav.intel"),
        icon: <GlobalOutlined />,
      },
    ],
  },
  {
    path: "/operate",
    name: t("nav.operate"),
    icon: <ToolOutlined />,
    routes: [
      {
        path: "/operate/pipeline",
        name: t("nav.pipeline"),
        icon: <CloudServerOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN"],
      },
      {
        path: "/operate/device-control",
        name: t("nav.deviceControl"),
        icon: <ToolOutlined />,
      },
      {
        path: "/operate/ml",
        name: t("nav.ml"),
        icon: <ExperimentOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN"],
      },
    ],
  },
  {
    path: "/admin",
    name: t("nav.admin"),
    icon: <TeamOutlined />,
    routes: [
      {
        path: "/admin/customers",
        name: t("nav.customersDevices"),
        icon: <TeamOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN"],
      },
      {
        path: "/admin/users",
        name: t("nav.users"),
        icon: <UserOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN", "CUSTOMER_ADMIN"],
      },
      {
        path: "/admin/tenants",
        name: t("nav.tenants"),
        icon: <ApartmentOutlined />,
        requiredRoles: ["SUPER_ADMIN"],
      },
    ],
  },
  {
    path: "/config",
    name: t("nav.config"),
    icon: <SettingOutlined />,
    routes: [
      {
        path: "/config/general",
        name: t("nav.generalConfig"),
        icon: <SettingOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN"],
      },
      {
        path: "/config/notifications",
        name: t("nav.notificationConfig"),
        icon: <BellOutlined />,
      },
      {
        path: "/config/integrations",
        name: t("nav.integrationConfig"),
        icon: <ApiOutlined />,
        requiredRoles: ["SUPER_ADMIN"],
      },
      { path: "/config/ai", name: t("nav.aiConfig"), icon: <RobotOutlined /> },
      {
        path: "/config/plugins",
        name: t("nav.pluginConfig"),
        icon: <AppstoreOutlined />,
        requiredRoles: ["SUPER_ADMIN", "TENANT_ADMIN"],
      },
    ],
  },
];

function filterMenuRoutes(
  routes: MenuRoute[],
  roles: AppRole[],
  isSuperAdmin: boolean,
): MenuRoute[] {
  return routes
    .map((route) => {
      const filteredChildren = route.routes
        ? filterMenuRoutes(route.routes, roles, isSuperAdmin)
        : undefined;
      const hasAccess =
        !route.requiredRoles ||
        isSuperAdmin ||
        route.requiredRoles.some((role) => roles.includes(role));

      if (!hasAccess) return null;
      if (route.routes && filteredChildren?.length === 0) return null;
      return { ...route, routes: filteredChildren };
    })
    .filter(Boolean) as MenuRoute[];
}

function toMenuItems(routes: MenuRoute[]): MenuProps["items"] {
  return routes.map((route) => ({
    key: route.path,
    icon: route.icon,
    label: route.routes ? (
      route.name
    ) : (
      <Link to={route.path}>{route.name}</Link>
    ),
    children: route.routes ? toMenuItems(route.routes) : undefined,
  }));
}

function findRoute(
  routes: MenuRoute[],
  pathname: string,
): MenuRoute | undefined {
  for (const route of routes) {
    if (route.path === pathname) return route;
    const child = route.routes ? findRoute(route.routes, pathname) : undefined;
    if (child) return child;
  }
  return undefined;
}

function firstAccessiblePath(route: MenuRoute): string {
  return route.routes?.length
    ? firstAccessiblePath(route.routes[0])
    : route.path;
}

const collapsedSubmenuStyle: CSSProperties = {
  height: 0,
  opacity: 0,
  transform: "translate3d(0, -5px, 0)",
};

function readCurrentSubmenuStyle(node: HTMLElement): CSSProperties {
  const computedStyle = window.getComputedStyle(node);
  const opacity = Number.parseFloat(computedStyle.opacity);
  return {
    height: node.getBoundingClientRect().height,
    opacity: Number.isFinite(opacity) ? opacity : 1,
    transform:
      computedStyle.transform === "none"
        ? "translate3d(0, 0, 0)"
        : computedStyle.transform,
  };
}

const sidebarSubmenuMotion: MenuProps["motion"] = {
  motionName: "tds-submenu-motion",
  motionAppear: false,
  motionDeadline: 360,
  onEnterStart: (node) => {
    const shouldResume = node.dataset.tdsSubmenuMotion === "active";
    const startStyle = shouldResume
      ? readCurrentSubmenuStyle(node)
      : collapsedSubmenuStyle;
    node.dataset.tdsSubmenuMotion = "active";
    return startStyle;
  },
  onEnterActive: (node) => {
    node.dataset.tdsSubmenuMotion = "active";
    return {
      height: node.scrollHeight,
      opacity: 1,
      transform: "translate3d(0, 0, 0)",
    };
  },
  onLeaveStart: (node) => {
    node.dataset.tdsSubmenuMotion = "active";
    return readCurrentSubmenuStyle(node);
  },
  onLeaveActive: (node) => {
    node.dataset.tdsSubmenuMotion = "active";
    return collapsedSubmenuStyle;
  },
  onEnterEnd: (node, event) => {
    const isComplete =
      event?.deadline === true ||
      (Boolean(event) &&
        "propertyName" in event &&
        event.propertyName === "height");
    if (isComplete) delete node.dataset.tdsSubmenuMotion;
    return isComplete;
  },
  onLeaveEnd: (node, event) => {
    const isComplete =
      event?.deadline === true ||
      (Boolean(event) &&
        "propertyName" in event &&
        event.propertyName === "height");
    if (isComplete) delete node.dataset.tdsSubmenuMotion;
    return isComplete;
  },
};

interface MenuIndicatorGeometry {
  x: number;
  y: number;
  width: number;
  height: number;
  opacity: number;
}

interface SlidingNavigationMenuProps {
  compact: boolean;
  defaultOpenKeys: string[];
  items: MenuProps["items"];
  onNavigate: () => void;
  selectedKey: string;
}

function SlidingNavigationMenu({
  compact,
  defaultOpenKeys,
  items,
  onNavigate,
  selectedKey,
}: SlidingNavigationMenuProps) {
  const shellRef = useRef<HTMLDivElement>(null);
  const indicatorRef = useRef<HTMLSpanElement>(null);
  const indicatorGeometryRef = useRef<MenuIndicatorGeometry | null>(null);
  const hasMeasuredRef = useRef(false);
  const previousCompactRef = useRef(compact);
  const previousSelectedKeyRef = useRef(selectedKey);
  const [openKeys, setOpenKeys] = useState<string[]>(() => defaultOpenKeys);
  const activeParentKey = defaultOpenKeys.find((key) =>
    selectedKey.startsWith(`${key}/`),
  );

  useLayoutEffect(() => {
    if (!activeParentKey) return;
    setOpenKeys((currentKeys) =>
      currentKeys.includes(activeParentKey)
        ? currentKeys
        : [...currentKeys, activeParentKey],
    );
  }, [activeParentKey]);

  useLayoutEffect(() => {
    const shell = shellRef.current;
    const indicatorElement = indicatorRef.current;
    if (!shell || !indicatorElement) return undefined;

    let animationFrame = 0;
    let submenuMotionFrame = 0;
    let motionTimer = 0;
    const menu = shell.querySelector<HTMLElement>(".tds-primary-menu");
    const shouldAnimateGeometry =
      hasMeasuredRef.current &&
      (previousCompactRef.current !== compact ||
        previousSelectedKeyRef.current !== selectedKey);
    previousCompactRef.current = compact;
    previousSelectedKeyRef.current = selectedKey;
    indicatorElement.classList.toggle(
      "tds-menu-selection-indicator--animate",
      shouldAnimateGeometry,
    );
    if (shouldAnimateGeometry) {
      motionTimer = window.setTimeout(() => {
        indicatorElement.classList.remove(
          "tds-menu-selection-indicator--animate",
        );
      }, 280);
    }

    const measure = () => {
      const selectedItem = shell.querySelector<HTMLElement>(
        ".ant-menu-item-selected",
      );
      if (!selectedItem) {
        indicatorElement.style.opacity = "0";
        return;
      }

      const shellRect = shell.getBoundingClientRect();
      const itemRect = selectedItem.getBoundingClientRect();
      const itemStyle = window.getComputedStyle(selectedItem);
      const round = (value: number) => Math.round(value * 10) / 10;
      const itemOpacity = Number.parseFloat(itemStyle.opacity);
      const isVisible =
        itemRect.width > 0 &&
        itemRect.height > 0 &&
        itemRect.bottom > shellRect.top &&
        itemRect.top < shellRect.bottom;
      const nextGeometry = {
        x: round(itemRect.left - shellRect.left),
        y: round(itemRect.top - shellRect.top),
        width: round(itemRect.width),
        height: round(itemRect.height),
        opacity: isVisible && Number.isFinite(itemOpacity) ? itemOpacity : 0,
      };

      hasMeasuredRef.current = true;
      const currentGeometry = indicatorGeometryRef.current;
      if (
        currentGeometry &&
        currentGeometry.x === nextGeometry.x &&
        currentGeometry.y === nextGeometry.y &&
        currentGeometry.width === nextGeometry.width &&
        currentGeometry.height === nextGeometry.height &&
        currentGeometry.opacity === nextGeometry.opacity
      ) {
        return;
      }

      indicatorGeometryRef.current = nextGeometry;
      indicatorElement.style.width = `${nextGeometry.width}px`;
      indicatorElement.style.height = `${nextGeometry.height}px`;
      indicatorElement.style.opacity = String(nextGeometry.opacity);
      indicatorElement.style.transform = `translate3d(${nextGeometry.x}px, ${nextGeometry.y}px, 0)`;
    };

    const scheduleMeasure = () => {
      window.cancelAnimationFrame(animationFrame);
      animationFrame = window.requestAnimationFrame(measure);
    };

    const trackSubmenuMotion = () => {
      if (submenuMotionFrame) return;
      const track = () => {
        measure();
        const submenuIsMoving = Boolean(
          menu?.querySelector(
            ".tds-submenu-motion-enter-active, .tds-submenu-motion-leave-active",
          ),
        );
        if (submenuIsMoving) {
          submenuMotionFrame = window.requestAnimationFrame(track);
        } else {
          submenuMotionFrame = 0;
          measure();
        }
      };
      submenuMotionFrame = window.requestAnimationFrame(track);
    };

    const handleTransitionRun = (event: TransitionEvent) => {
      const target = event.target;
      if (
        event.propertyName === "height" &&
        target instanceof HTMLElement &&
        target.classList.contains("tds-submenu-motion")
      ) {
        trackSubmenuMotion();
      }
    };

    const handleTransitionEnd = (event: TransitionEvent) => {
      const target = event.target;
      if (
        event.propertyName === "height" &&
        target instanceof HTMLElement &&
        target.classList.contains("tds-submenu-motion")
      ) {
        scheduleMeasure();
      }
    };

    measure();
    menu?.addEventListener("scroll", scheduleMeasure, { passive: true });
    menu?.addEventListener("transitionrun", handleTransitionRun);
    menu?.addEventListener("transitionend", handleTransitionEnd);
    menu?.addEventListener("transitioncancel", handleTransitionEnd);
    window.addEventListener("resize", scheduleMeasure, { passive: true });

    const resizeObserver =
      typeof ResizeObserver === "undefined"
        ? null
        : new ResizeObserver(scheduleMeasure);
    resizeObserver?.observe(shell);
    const selectedItem = shell.querySelector<HTMLElement>(
      ".ant-menu-item-selected",
    );
    if (selectedItem) resizeObserver?.observe(selectedItem);

    const mutationObserver =
      typeof MutationObserver === "undefined" || !menu
        ? null
        : new MutationObserver(scheduleMeasure);
    if (mutationObserver && menu) {
      mutationObserver.observe(menu, {
        attributes: true,
        attributeFilter: ["class", "style"],
        subtree: true,
      });
    }

    return () => {
      window.cancelAnimationFrame(animationFrame);
      window.cancelAnimationFrame(submenuMotionFrame);
      window.clearTimeout(motionTimer);
      indicatorElement.classList.remove(
        "tds-menu-selection-indicator--animate",
      );
      menu?.removeEventListener("scroll", scheduleMeasure);
      menu?.removeEventListener("transitionrun", handleTransitionRun);
      menu?.removeEventListener("transitionend", handleTransitionEnd);
      menu?.removeEventListener("transitioncancel", handleTransitionEnd);
      window.removeEventListener("resize", scheduleMeasure);
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
    };
  }, [compact, selectedKey]);

  return (
    <div className="tds-menu-shell" ref={shellRef}>
      <Menu
        className="tds-primary-menu"
        theme="dark"
        mode="inline"
        inlineCollapsed={compact}
        items={items}
        selectedKeys={[selectedKey]}
        openKeys={openKeys}
        motion={sidebarSubmenuMotion}
        expandIcon={({ isOpen }) => (
          <DownOutlined
            className={`tds-submenu-chevron${isOpen ? " tds-submenu-chevron--open" : ""}`}
          />
        )}
        onOpenChange={setOpenKeys}
        onClick={onNavigate}
      />
      <span
        ref={indicatorRef}
        aria-hidden="true"
        className="tds-menu-selection-indicator"
      />
    </div>
  );
}

interface RouteTransitionProps {
  children: ReactNode;
  routeKey: string;
}

function RouteTransition({ children, routeKey }: RouteTransitionProps) {
  const stageRef = useRef<HTMLDivElement>(null);
  const animationRef = useRef<Animation | null>(null);
  const previousRouteKeyRef = useRef(routeKey);

  useLayoutEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    if (previousRouteKeyRef.current === routeKey) {
      return;
    }
    previousRouteKeyRef.current = routeKey;

    const prefersReducedMotion = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    const runningAnimation = animationRef.current;
    let startOpacity = "0";
    let startTransform = "translate3d(0, 7px, 0)";

    if (runningAnimation?.playState === "running") {
      const currentStyle = window.getComputedStyle(stage);
      startOpacity = currentStyle.opacity;
      startTransform =
        currentStyle.transform === "none"
          ? "translate3d(0, 0, 0)"
          : currentStyle.transform;
    }
    runningAnimation?.cancel();

    if (prefersReducedMotion) {
      animationRef.current = null;
      return;
    }

    const animation = stage.animate(
      [
        { opacity: startOpacity, transform: startTransform },
        { opacity: "1", transform: "translate3d(0, 0, 0)" },
      ],
      {
        duration: 230,
        easing: "cubic-bezier(0.16, 1, 0.3, 1)",
        fill: "both",
      },
    );
    animationRef.current = animation;
    animation.onfinish = () => {
      if (animationRef.current === animation) {
        animation.cancel();
        animationRef.current = null;
      }
    };
  }, [routeKey]);

  useEffect(() => () => animationRef.current?.cancel(), []);

  return (
    <div
      ref={stageRef}
      className="tds-route-stage"
      data-route={routeKey}
      data-route-stage="true"
    >
      {children}
    </div>
  );
}

function getWorkspaceDefinitions(isEnglish: boolean): WorkspaceDefinition[] {
  return [
    {
      id: "monitoring",
      name: isEnglish ? "Threat operations" : "威胁运营",
      description: isEnglish ? "Monitor and investigate" : "监测、研判与处置",
      accent: "#19b7a4",
      icon: <SafetyCertificateOutlined />,
      roots: ["/overview", "/investigate"],
      defaultPath: "/overview",
      matches: (pathname) =>
        pathname === "/overview" || pathname.startsWith("/investigate"),
    },
    {
      id: "operations",
      name: isEnglish ? "Platform health" : "平台运行",
      description: isEnglish ? "Pipelines and models" : "链路、模型与服务",
      accent: "#4e8cff",
      icon: <CloudServerOutlined />,
      roots: ["/operate"],
      defaultPath: "/operate/pipeline",
      matches: (pathname) => pathname.startsWith("/operate"),
    },
    {
      id: "governance",
      name: isEnglish ? "Organization" : "资产与组织",
      description: isEnglish
        ? "Tenants, customers and users"
        : "租户、客户与用户",
      accent: "#d99a38",
      icon: <ApartmentOutlined />,
      roots: ["/admin"],
      defaultPath: "/admin/customers",
      matches: (pathname) => pathname.startsWith("/admin"),
    },
    {
      id: "configuration",
      name: isEnglish ? "Configuration" : "平台配置",
      description: isEnglish ? "Policies and integrations" : "策略、通知与集成",
      accent: "#9d7aea",
      icon: <SettingOutlined />,
      roots: ["/config"],
      defaultPath: "/config/general",
      matches: (pathname) => pathname.startsWith("/config"),
    },
  ];
}

function AppLayout() {
  const { user, logout, isSuperAdmin } = useAuth();
  const { initialized: scopeInitialized } = useScope();
  const { isDarkMode, toggleTheme } = useTheme();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const screens = useBreakpoint();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false);
  const roles = useMemo(() => user?.roles ?? [], [user]);
  const isDesktop = screens.lg !== false;
  const currentLang = i18n.resolvedLanguage === "en-US" ? "en-US" : "zh-CN";
  const isEnglish = currentLang === "en-US";

  const menuRoutes = useMemo(() => {
    const allMenuRoutes = getAllMenuRoutes(t);
    return filterMenuRoutes(allMenuRoutes, roles, isSuperAdmin);
  }, [t, roles, isSuperAdmin]);

  const workspaceDefinitions = useMemo(
    () =>
      getWorkspaceDefinitions(isEnglish).filter((workspace) =>
        workspace.roots.some((root) =>
          menuRoutes.some((route) => route.path === root),
        ),
      ),
    [isEnglish, menuRoutes],
  );

  const routeWorkspace = workspaceDefinitions.find((workspace) =>
    workspace.matches(location.pathname),
  );
  const [workspaceId, setWorkspaceId] = useState<WorkspaceId>(
    routeWorkspace?.id ?? "monitoring",
  );

  useEffect(() => {
    if (routeWorkspace) setWorkspaceId(routeWorkspace.id);
  }, [routeWorkspace]);

  const activeWorkspace =
    routeWorkspace ??
    workspaceDefinitions.find((workspace) => workspace.id === workspaceId) ??
    workspaceDefinitions[0] ??
    getWorkspaceDefinitions(isEnglish)[0];
  const activeRoutes = menuRoutes.filter((route) =>
    activeWorkspace.roots.includes(route.path),
  );
  const activeRoute = findRoute(menuRoutes, location.pathname);
  const pageTitle = activeRoute?.name ?? activeWorkspace.name;
  const version = import.meta.env.VITE_APP_VERSION || "dev";

  useEffect(() => {
    document.documentElement.style.setProperty(
      "--workspace-accent",
      activeWorkspace.accent,
    );
    return () => {
      document.documentElement.style.removeProperty("--workspace-accent");
    };
  }, [activeWorkspace.accent]);

  const changeWorkspace = (nextId: WorkspaceId) => {
    const nextWorkspace = workspaceDefinitions.find(
      (workspace) => workspace.id === nextId,
    );
    setWorkspaceMenuOpen(false);
    if (!nextWorkspace || nextWorkspace.id === activeWorkspace.id) return;
    const nextRoot = menuRoutes.find((route) =>
      nextWorkspace.roots.includes(route.path),
    );
    document.documentElement.style.setProperty(
      "--workspace-accent",
      nextWorkspace.accent,
    );
    setWorkspaceId(nextWorkspace.id);
    setMobileNavigationOpen(false);
    navigate(
      nextRoot ? firstAccessiblePath(nextRoot) : nextWorkspace.defaultPath,
    );
  };

  const workspaceMenu: MenuProps = {
    selectedKeys: [activeWorkspace.id],
    onClick: ({ key }) => changeWorkspace(key as WorkspaceId),
    items: workspaceDefinitions.map((workspace) => ({
      key: workspace.id,
      label: (
        <span className="tds-workspace-option">
          <span
            className="tds-workspace-option__icon"
            style={{ "--option-accent": workspace.accent } as CSSProperties}
          >
            {workspace.icon}
          </span>
          <span className="tds-workspace-option__copy">
            <strong>{workspace.name}</strong>
            <small>{workspace.description}</small>
          </span>
          {workspace.id === activeWorkspace.id ? (
            <CheckOutlined className="tds-workspace-option__check" />
          ) : null}
        </span>
      ),
    })),
  };

  const accountMenu: MenuProps = {
    items: [
      {
        key: "identity",
        disabled: true,
        label: (
          <span className="tds-account-summary">
            <strong>
              {user?.displayName || user?.username || t("app.user")}
            </strong>
            <small>{user?.username}</small>
          </span>
        ),
      },
      { type: "divider" },
      { key: "logout", icon: <LogoutOutlined />, label: t("app.logout") },
    ],
    onClick: ({ key }) => {
      if (key === "logout") logout();
    },
  };

  const navigation = (compact = false, mobile = false) => (
    <div
      className={`tds-navigation ${compact ? "tds-navigation--compact" : ""}`}
    >
      <div className="tds-navigation__top">
        <Dropdown
          menu={workspaceMenu}
          trigger={["click"]}
          placement="bottomLeft"
          overlayClassName="tds-workspace-dropdown"
          open={workspaceMenuOpen}
          onOpenChange={setWorkspaceMenuOpen}
        >
          <button
            type="button"
            className="tds-workspace-switcher"
            aria-label={isEnglish ? "Switch workspace" : "切换工作区"}
            aria-haspopup="menu"
            aria-expanded={workspaceMenuOpen}
            onKeyDown={(event) => {
              if (
                event.key === "Enter" ||
                event.key === " " ||
                event.key === "ArrowDown"
              ) {
                event.preventDefault();
                setWorkspaceMenuOpen(true);
              }
            }}
          >
            <span className="tds-workspace-switcher__mark">
              {activeWorkspace.icon}
            </span>
            <span
              className="tds-workspace-switcher__labels"
              key={activeWorkspace.id}
            >
              <small>{t("app.title")}</small>
              <strong>{activeWorkspace.name}</strong>
            </span>
            <DownOutlined className="tds-workspace-switcher__chevron" />
          </button>
        </Dropdown>
        {!compact ? (
          <div className="tds-navigation__context">
            {activeWorkspace.description}
          </div>
        ) : null}
      </div>

      {mobile ? (
        <div className="tds-mobile-scope">
          <ScopeSelector />
        </div>
      ) : null}

      <SlidingNavigationMenu
        key={activeWorkspace.id}
        compact={compact}
        items={toMenuItems(activeRoutes)}
        defaultOpenKeys={activeRoutes
          .filter((route) => route.routes?.length)
          .map((route) => route.path)}
        selectedKey={location.pathname}
        onNavigate={() => {
          setMobileNavigationOpen(false);
        }}
      />

      <div className="tds-navigation__footer">
        <span className="tds-runtime-dot" aria-hidden="true" />
        {!compact ? (
          <span>
            <strong>{isEnglish ? "Console online" : "控制台在线"}</strong>
            <small>v{version}</small>
          </span>
        ) : null}
      </div>
    </div>
  );

  const shellStyle = {
    "--workspace-accent": activeWorkspace.accent,
  } as CSSProperties;

  return (
    <ConfigProvider
      locale={isEnglish ? enUS : zhCN}
      theme={createAppTheme({ isDarkMode, accent: activeWorkspace.accent })}
    >
      <a className="tds-skip-link" href="#main-content">
        {isEnglish ? "Skip to main content" : "跳到主要内容"}
      </a>
      {/*
        INTERACTION-011: machine-readable readiness, invisible to users and
        assistive technology. The shell only exists once the root Suspense has
        resolved; `data-route-path` is the *committed* route (router updates
        run in a transition, so it lags the URL until the lazy page has
        rendered); `data-app-ready` follows the real Scope initialisation.
      */}
      <Layout
        className="tds-shell"
        style={shellStyle}
        data-app-ready={scopeInitialized ? "true" : "false"}
        data-route-path={location.pathname}
      >
        {isDesktop ? (
          <Sider
            className="tds-sider"
            width={252}
            collapsedWidth={76}
            collapsible
            collapsed={collapsed}
            onCollapse={setCollapsed}
            theme="dark"
          >
            {navigation(collapsed)}
          </Sider>
        ) : (
          <Drawer
            open={mobileNavigationOpen}
            onClose={() => setMobileNavigationOpen(false)}
            placement="left"
            width={280}
            closable={false}
            rootClassName="tds-mobile-navigation"
            styles={{ body: { padding: 0, background: "#101c2c" } }}
          >
            {navigation(false, true)}
          </Drawer>
        )}

        <Layout className="tds-main-layout">
          <Header className="tds-header">
            <div className="tds-header__leading">
              {!isDesktop ? (
                <Button
                  type="text"
                  className="tds-mobile-menu-button"
                  icon={<MenuOutlined />}
                  onClick={() => setMobileNavigationOpen(true)}
                  aria-label={isEnglish ? "Open navigation" : "打开导航"}
                />
              ) : null}
              <div className="tds-page-context">
                <span>{activeWorkspace.name}</span>
                <h1>{pageTitle}</h1>
              </div>
            </div>

            <div className="tds-header__actions">
              <div className="tds-header__scope">
                <ScopeSelector />
              </div>
              <Tooltip title={t("app.toggleTheme")}>
                <Button
                  type="text"
                  className="tds-icon-button"
                  onClick={toggleTheme}
                  aria-label={t("app.toggleTheme")}
                >
                  <span className="tds-theme-icon" aria-hidden="true">
                    <span
                      className={`tds-theme-icon__item${isDarkMode ? " tds-theme-icon__item--active" : ""}`}
                    >
                      <SunOutlined />
                    </span>
                    <span
                      className={`tds-theme-icon__item${!isDarkMode ? " tds-theme-icon__item--active" : ""}`}
                    >
                      <MoonOutlined />
                    </span>
                  </span>
                </Button>
              </Tooltip>
              <Tooltip title={t("app.switchLanguage")}>
                <Button
                  type="text"
                  className="tds-language-button"
                  onClick={() =>
                    i18n.changeLanguage(isEnglish ? "zh-CN" : "en-US")
                  }
                  aria-label={t("app.switchLanguage")}
                >
                  {isEnglish ? "中" : "EN"}
                </Button>
              </Tooltip>
              <Dropdown
                menu={accountMenu}
                trigger={["click"]}
                placement="bottomRight"
              >
                <button
                  type="button"
                  className="tds-user-menu"
                  aria-label={t("app.user")}
                >
                  <Avatar size={30} icon={<UserOutlined />} />
                  <span className="tds-user-menu__copy">
                    <strong>
                      {user?.displayName || user?.username || t("app.user")}
                    </strong>
                    <Text type="secondary">{roles[0]?.replace(/_/g, " ")}</Text>
                  </span>
                  <DownOutlined />
                </button>
              </Dropdown>
            </div>
          </Header>

          <Content id="main-content" className="tds-content">
            <div className="tds-content__inner">
              <ErrorBoundary>
                <RouteTransition routeKey={location.pathname}>
                  <Routes location={location}>
                    <Route
                      path="/"
                      element={<Navigate to="/overview" replace />}
                    />
                    <Route path="/overview" element={<Overview />} />

                    <Route
                      path="/investigate"
                      element={<Navigate to="/investigate/alerts" replace />}
                    />
                    <Route
                      path="/investigate/alerts"
                      element={<AlertCenter />}
                    />
                    <Route
                      path="/investigate/threats"
                      element={<ThreatList />}
                    />
                    <Route
                      path="/investigate/intel"
                      element={<ThreatIntel />}
                    />

                    <Route
                      path="/operate"
                      element={<Navigate to={roles.includes("SUPER_ADMIN") || roles.includes("TENANT_ADMIN") ? "/operate/pipeline" : "/operate/device-control"} replace />}
                    />
                    <Route path="/operate/device-control" element={<DeviceControlDevices />} />
                    <Route
                      path="/operate/pipeline"
                      element={
                        <RouteGuard
                          requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                        >
                          <PipelineHealth />
                        </RouteGuard>
                      }
                    />
                    <Route
                      path="/operate/ml"
                      element={
                        <RouteGuard
                          requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                        >
                          <MlDetection />
                        </RouteGuard>
                      }
                    />

                    <Route
                      path="/admin"
                      element={
                        <Navigate
                          to={
                            roles.includes("CUSTOMER_ADMIN")
                              ? "/admin/users"
                              : "/admin/customers"
                          }
                          replace
                        />
                      }
                    />
                    <Route
                      path="/admin/customers"
                      element={
                        <RouteGuard
                          requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                        >
                          <CustomersAndDevices />
                        </RouteGuard>
                      }
                    />
                    <Route
                      path="/admin/users"
                      element={
                        <RouteGuard
                          requiredRoles={[
                            "SUPER_ADMIN",
                            "TENANT_ADMIN",
                            "CUSTOMER_ADMIN",
                          ]}
                        >
                          <UserMgmt />
                        </RouteGuard>
                      }
                    />
                    <Route
                      path="/admin/tenants"
                      element={
                        <RouteGuard requiredRoles={["SUPER_ADMIN"]}>
                          <TenantMgmt />
                        </RouteGuard>
                      }
                    />

                    <Route
                      path="/config"
                      element={<Navigate to="/config/general" replace />}
                    />
                    <Route
                      path="/config/general"
                      element={
                        <RouteGuard
                          requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                        >
                          <GeneralConfig />
                        </RouteGuard>
                      }
                    />
                    <Route
                      path="/config/notifications"
                      element={<NotificationConfig />}
                    />
                    <Route
                      path="/config/integrations"
                      element={
                        <RouteGuard requiredRoles={["SUPER_ADMIN"]}>
                          <IntegrationConfig />
                        </RouteGuard>
                      }
                    />
                    <Route path="/config/ai" element={<AIConfig />} />
                    <Route
                      path="/config/plugins"
                      element={
                        <RouteGuard
                          requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                        >
                          <PluginConfig />
                        </RouteGuard>
                      }
                    />

                    <Route
                      path="/threats"
                      element={<Navigate to="/investigate/threats" replace />}
                    />
                    <Route
                      path="/alerts"
                      element={<Navigate to="/investigate/alerts" replace />}
                    />
                    <Route
                      path="/threat-intel"
                      element={<Navigate to="/investigate/intel" replace />}
                    />
                    <Route
                      path="/pipeline"
                      element={<Navigate to="/operate/pipeline" replace />}
                    />
                    <Route
                      path="/ml"
                      element={<Navigate to="/operate/ml" replace />}
                    />
                    <Route
                      path="/customers-devices"
                      element={<Navigate to="/admin/customers" replace />}
                    />
                    <Route
                      path="/tenants"
                      element={<Navigate to="/admin/tenants" replace />}
                    />
                    <Route
                      path="/users"
                      element={<Navigate to="/admin/users" replace />}
                    />
                    <Route
                      path="/configuration"
                      element={<Navigate to="/config/general" replace />}
                    />
                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </RouteTransition>
              </ErrorBoundary>
            </div>
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}

function App() {
  return (
    <BrowserRouter future={{ v7_startTransition: true }}>
      <AuthProvider>
        <DocumentLanguage />
        <Suspense
          fallback={
            <div className="tds-app-loading" role="status" aria-label="Loading">
              <span className="tds-app-loading__mark">
                <SafetyCertificateOutlined />
              </span>
              <Spin size="small" />
            </div>
          }
        >
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/*"
              element={
                <RouteGuard>
                  <ScopeProvider>
                    <AppLayout />
                  </ScopeProvider>
                </RouteGuard>
              }
            />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}

function DocumentLanguage() {
  const { i18n } = useTranslation();

  useEffect(() => {
    document.documentElement.lang =
      i18n.resolvedLanguage === "en-US" ? "en-US" : "zh-CN";
  }, [i18n.resolvedLanguage]);

  return null;
}

export default App;
