/**
 * INTERACTION-004 test-only harness component for the ConfigCascading tables.
 *
 * Renders the real component with the production antd theme
 * (createAppTheme), global CSS and i18n, inside the same
 * `.tds-content > .tds-content__inner > .tds-route-stage` shell the router
 * uses (no extra wrappers that would change its layout).
 *
 * INTERACTION-009: this module only defines and exports the component; the
 * browser bootstrap (providers + createRoot) lives in config-cascading.tsx.
 * Keep it free of other runtime exports so React Fast Refresh (and the
 * react-refresh/only-export-components lint rule) can treat it as a pure
 * component module. Nothing here is imported by production code.
 */
import type { CSSProperties } from "react";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import enUS from "antd/locale/en_US";
import { useTranslation } from "react-i18next";
import "@/i18n";
import "@/index.css";
import "@/pages/Configuration/configuration.css";
import { useTheme } from "@/contexts/ThemeContext";
import { createAppTheme } from "@/theme/appTheme";
import ConfigCascading from "@/pages/Configuration/ConfigCascading";

/** Accent of the "configuration" workspace in App.tsx. */
const CONFIGURATION_ACCENT = "#9d7aea";

export function HarnessShell() {
  const { isDarkMode } = useTheme();
  const { i18n } = useTranslation();
  const isEnglish = i18n.language === "en-US";
  return (
    <ConfigProvider
      locale={isEnglish ? enUS : zhCN}
      theme={createAppTheme({ isDarkMode, accent: CONFIGURATION_ACCENT })}
    >
      <div
        className="tds-shell"
        style={{ "--workspace-accent": CONFIGURATION_ACCENT } as CSSProperties}
      >
        {/* Same shell as App.tsx: .tds-content > .tds-content__inner > .tds-route-stage */}
        {/* INTERACTION-011: same readiness contract as the app shell (no Scope here). */}
        <main
          id="main-content"
          className="tds-content"
          data-testid="config-cascading-harness"
          data-app-ready="true"
          data-route-path={window.location.pathname}
        >
          <div className="tds-content__inner">
            <div className="tds-route-stage">
              <ConfigCascading />
            </div>
          </div>
        </main>
      </div>
    </ConfigProvider>
  );
}
