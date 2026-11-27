import type { ChartDataPoint, ThreatTrendPoint } from '@/types';
import { formatHourMinute, formatMonthDay } from '@/utils/formatDateTime';

/** Ranges longer than two days are labelled by day, shorter ones by time. */
export const DAY_LABEL_THRESHOLD_HOURS = 48;

/**
 * INTERACTION-008: the single mapping from the trend API DTO
 * (`timestamp`/`count`, backend `TrendDataPoint`) to the chart model
 * (`time`/`value`). Nothing else may read API trend fields for charts.
 */
export function toTrendChartData(
  points: readonly ThreatTrendPoint[] | null | undefined,
  hours: number,
  category: string,
): ChartDataPoint[] {
  const formatLabel =
    hours > DAY_LABEL_THRESHOLD_HOURS ? formatMonthDay : formatHourMinute;
  return (points ?? []).map((point) => ({
    time: formatLabel(point.timestamp),
    value: point.count,
    category,
  }));
}
