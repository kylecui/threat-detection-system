import { ColumnHeightOutlined } from "@ant-design/icons";
import { useIntl } from "@ant-design/pro-components";
import { Dropdown, Tooltip } from "antd";

export type TableDensity = "large" | "middle" | "small";

interface TableDensityControlProps {
  value: TableDensity;
  onChange: (value: TableDensity) => void;
}

/**
 * INTERACTION-012: app-owned replacement for ProTable's built-in density
 * toolbar item (same icon, menu, sizes and Pro Components labels).
 *
 * The built-in item renders `Tooltip > React.memo(DensityIcon)`: the memo
 * component cannot hold a ref, so the Tooltip's ResizeObserver falls back to
 * `findDOMNode`, which React StrictMode reports as a console error. Here the
 * Tooltip wraps a native <button>, which also makes the control
 * keyboard-focusable.
 */
export default function TableDensityControl({ value, onChange }: TableDensityControlProps) {
  const intl = useIntl();
  const label = intl.getMessage("tableToolBar.density", "表格密度");
  return (
    <Dropdown
      trigger={["click"]}
      menu={{
        selectable: true,
        selectedKeys: [value],
        onClick: ({ key }: { key: string }) => onChange(key as TableDensity),
        style: { width: 80 },
        items: [
          { key: "large", label: intl.getMessage("tableToolBar.densityLarger", "宽松") },
          { key: "middle", label: intl.getMessage("tableToolBar.densityMiddle", "中等") },
          { key: "small", label: intl.getMessage("tableToolBar.densitySmall", "紧凑") },
        ],
      }}
    >
      <Tooltip title={label}>
        <button type="button" className="tds-table-density-control" aria-label={label}>
          <ColumnHeightOutlined />
        </button>
      </Tooltip>
    </Dropdown>
  );
}
