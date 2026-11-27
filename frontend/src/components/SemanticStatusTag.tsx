import type { ReactNode } from "react";
import { Tag } from "antd";

export type SemanticStatusTone =
  | "success"
  | "neutral"
  | "warning"
  | "danger"
  | "info";

type SemanticStatusTagProps = {
  children: ReactNode;
  className?: string;
  tone: SemanticStatusTone;
};

export default function SemanticStatusTag({
  children,
  className,
  tone,
}: SemanticStatusTagProps) {
  const classes = ["tds-status-tag", `tds-status-tag--${tone}`, className]
    .filter(Boolean)
    .join(" ");

  return <Tag className={classes}>{children}</Tag>;
}
