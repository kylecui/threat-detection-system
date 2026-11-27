/**
 * Keep alert owner cells readable even if an unexpected legacy payload reaches
 * the UI. The normal API contract is a username string; non-string values must
 * never leak into the table as "[object Object]".
 */
export const formatAlertOwner = (
  assignedTo: unknown,
  fallback = "-",
): string => {
  if (typeof assignedTo !== "string") return fallback;
  return assignedTo.trim() || fallback;
};
