/**
 * INTERACTION-013: app-owned hooks for ProTable query forms.
 *
 * ProTable puts `search.className` on the wrapper around its QueryFilter, so
 * global CSS (`.tds-content .tds-table-query`) and E2E locate the query form
 * through a class this project owns instead of Pro Components' internal
 * class names (wrapper / QueryFilter roots), which are not a public API and
 * have changed between versions.
 */
export const TABLE_QUERY_CLASS = "tds-table-query";

/** Default `search` prop for every ProTable (keeps `labelWidth: "auto"`). */
export const PRO_TABLE_SEARCH = {
  labelWidth: "auto",
  className: TABLE_QUERY_CLASS,
} as const;
