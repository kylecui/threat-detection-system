import { useCallback, useState, type ReactNode } from "react";
import TableDensityControl, { type TableDensity } from "./TableDensityControl";

/**
 * INTERACTION-012: ProTable toolbar props shared by every ProTable.
 *
 * Keeps the default toolbar (reload, density, column settings) and its order,
 * but replaces the built-in density item, which triggers React StrictMode
 * `findDOMNode` warnings, with the app-owned TableDensityControl driving
 * ProTable's controlled `size`. Spread the result onto each ProTable element.
 */
export function useProTableToolbar(defaultSize: TableDensity = "middle") {
  const [size, setSize] = useState<TableDensity>(defaultSize);
  const onSizeChange = useCallback((next: TableDensity | undefined) => setSize(next ?? defaultSize), [defaultSize]);
  const optionsRender = useCallback(
    (_props: unknown, defaultDom: ReactNode[]): ReactNode[] => {
      // defaultDom = [reload, setting] (built-in density disabled below).
      const [reload, ...rest] = defaultDom;
      return [reload, <TableDensityControl key="density" value={size} onChange={setSize} />, ...rest];
    },
    [size],
  );
  return {
    size,
    onSizeChange,
    options: { density: false, reload: true, setting: true },
    optionsRender,
  } as const;
}
