/**
 * INTERACTION-004 test-only harness entry for ConfigCascading
 * (loaded by config-cascading.html, served by the Vite dev server only).
 *
 * INTERACTION-009: bootstrap only — providers and createRoot. The harness
 * component itself lives in ConfigCascadingHarness.tsx so this entry defines
 * no React component (react-refresh/only-export-components, zero-warning lint).
 * Provider order is unchanged: ThemeProvider -> AuthProvider -> HarnessShell.
 */
import React from "react";
import ReactDOM from "react-dom/client";
// Imported before the providers so i18n and the global/configuration CSS are
// evaluated in the same order as before the split.
import { HarnessShell } from "./ConfigCascadingHarness";
import { ThemeProvider } from "@/contexts/ThemeContext";
import { AuthProvider } from "@/contexts/AuthContext";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <HarnessShell />
      </AuthProvider>
    </ThemeProvider>
  </React.StrictMode>,
);
