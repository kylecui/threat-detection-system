import { afterEach, describe, expect, test } from 'vitest';
import i18n from '@/i18n';
import type { ThreatTrendPoint } from '@/types';
import { formatHourMinute, formatMonthDay } from '@/utils/formatDateTime';
import { toTrendChartData } from '@/pages/Overview/trend';

const point = (timestamp: string, count: number): ThreatTrendPoint => ({
  timestamp,
  count,
  averageScore: 0,
  maxScore: 0,
  criticalCount: 0,
  highCount: 0,
  mediumCount: 0,
});

const T0 = '2025-12-31T08:00:00Z';
const T1 = '2026-01-02T09:30:00Z';

describe('toTrendChartData (INTERACTION-008)', () => {
  afterEach(async () => {
    await i18n.changeLanguage('zh-CN');
  });

  test('maps timestamp/count to time/value and keeps the category', () => {
    expect(toTrendChartData([point(T0, 3)], 24, 'Threats')).toEqual([
      { time: formatHourMinute(T0), value: 3, category: 'Threats' },
    ]);
  });

  test('keeps a zero count as 0 (empty buckets stay on the chart)', () => {
    const data = toTrendChartData([point(T0, 0), point(T1, 5)], 24, 'c');
    expect(data.map((chartPoint) => chartPoint.value)).toEqual([0, 5]);
  });

  test('returns an empty chart for empty or missing trends', () => {
    expect(toTrendChartData([], 24, 'c')).toEqual([]);
    expect(toTrendChartData(undefined, 24, 'c')).toEqual([]);
    expect(toTrendChartData(null, 168, 'c')).toEqual([]);
  });

  test('labels 24h by time of day and 7d / 30d by day', () => {
    expect(toTrendChartData([point(T1, 1)], 24, 'c')[0].time).toBe(formatHourMinute(T1));
    expect(toTrendChartData([point(T1, 1)], 48, 'c')[0].time).toBe(formatHourMinute(T1));
    expect(toTrendChartData([point(T1, 1)], 168, 'c')[0].time).toBe(formatMonthDay(T1));
    expect(toTrendChartData([point(T1, 1)], 720, 'c')[0].time).toBe(formatMonthDay(T1));
  });

  test('follows the active language', async () => {
    await i18n.changeLanguage('en-US');
    expect(toTrendChartData([point(T1, 1)], 168, 'c')[0].time).toBe(
      new Intl.DateTimeFormat('en-US', { month: '2-digit', day: '2-digit' }).format(new Date(T1)),
    );
  });

  test('never reads chart-model fields from the API payload', () => {
    // A legacy `time/value` payload is not a ThreatTrendPoint; it must not
    // silently produce data through a fallback.
    const legacy = [{ time: T0, value: 9 }] as unknown as ThreatTrendPoint[];
    const [chartPoint] = toTrendChartData(legacy, 24, 'c');
    expect(chartPoint.value).toBeUndefined();
    expect(chartPoint.time).toBe('-');
  });
});
