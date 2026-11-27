import { theme, type ThemeConfig } from "antd";

/**
 * Ant Design theme for the whole application (tokens + component tokens).
 *
 * Extracted from App.tsx (INTERACTION-004) so that test-only harnesses render
 * with exactly the production tokens; App.tsx remains the only production
 * caller.
 */
export interface AppThemeOptions {
  isDarkMode: boolean;
  /** Accent colour of the active workspace. */
  accent: string;
}

export function createAppTheme({ isDarkMode, accent }: AppThemeOptions): ThemeConfig {
  return {
    algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: accent,
      colorInfo: "#3f7fdb",
      colorSuccess: "#138a72",
      colorWarning: "#c27a16",
      colorError: "#d94c3d",
      colorBgLayout: isDarkMode ? "#0c1420" : "#f3f6f8",
      colorBgContainer: isDarkMode ? "#141f2e" : "#ffffff",
      colorText: isDarkMode ? "rgba(242, 247, 251, 0.9)" : "#172331",
      colorTextSecondary: isDarkMode
        ? "rgba(224, 234, 242, 0.62)"
        : "#607080",
      colorBorder: isDarkMode ? "#2a394a" : "#dce4e9",
      borderRadius: 8,
      borderRadiusLG: 12,
      controlHeight: 38,
      fontFamily:
        "Aptos, 'Microsoft YaHei UI', 'HarmonyOS Sans SC', 'Noto Sans SC', sans-serif",
      fontSize: 14,
      lineHeight: 1.55,
      boxShadow: isDarkMode
        ? "0 12px 32px rgba(0, 0, 0, 0.28)"
        : "0 12px 32px rgba(24, 43, 58, 0.10)",
    },
    components: {
      Button: { primaryShadow: "none", fontWeight: 600 },
      Card: { headerFontSize: 15, headerHeight: 50, bodyPadding: 20 },
      Drawer: { zIndexPopup: 1200 },
      Form: {
        itemMarginBottom: 18,
        labelColor: isDarkMode ? "#c5d0da" : "#344555",
      },
      Menu: {
        darkItemBg: "#101c2c",
        darkSubMenuItemBg: "#0d1826",
        darkItemColor: "rgba(226, 236, 243, 0.72)",
        darkItemHoverColor: "#ffffff",
        darkItemSelectedColor: "#ffffff",
        itemBorderRadius: 7,
        itemMarginInline: 10,
      },
      Modal: { borderRadiusLG: 14, titleFontSize: 17 },
      Table: {
        headerBg: isDarkMode ? "#182536" : "#f7f9fa",
        headerColor: isDarkMode ? "#dce5ec" : "#435363",
        rowHoverBg: isDarkMode ? "#182b38" : "#f4faf9",
        // INTERACTION-001: one density baseline for default / middle /
        // small tables. Row (48px) and header (40px) heights are set in
        // index.css; 3px block padding keeps a 40px icon-button row at
        // exactly 48px (40 + 3 + 3 + 1px divider).
        cellPaddingBlock: 3,
        cellPaddingInline: 12,
        cellPaddingBlockMD: 3,
        cellPaddingInlineMD: 12,
        cellPaddingBlockSM: 3,
        cellPaddingInlineSM: 12,
        cellFontSize: 14,
        cellFontSizeMD: 14,
        cellFontSizeSM: 14,
      },
      Tabs: { titleFontSize: 14, horizontalItemPadding: "11px 4px" },
    },
  };
}
