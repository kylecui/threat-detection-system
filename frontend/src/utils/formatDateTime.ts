import i18n from '@/i18n';

type DateValue = string | number | Date | null | undefined;
type DateTimeStyle = 'full' | 'minute' | 'monthDay' | 'hourMinute';

const formatters = new Map<string, Intl.DateTimeFormat>();

const optionsByStyle: Record<DateTimeStyle, Intl.DateTimeFormatOptions> = {
  full: {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  },
  minute: {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  },
  monthDay: {
    month: '2-digit',
    day: '2-digit',
  },
  hourMinute: {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  },
};

function resolveLocale(): string {
  return i18n.resolvedLanguage === 'en-US' ? 'en-US' : 'zh-CN';
}

function getFormatter(style: DateTimeStyle): Intl.DateTimeFormat {
  const locale = resolveLocale();
  const key = `${locale}:${style}`;
  const cached = formatters.get(key);
  if (cached) return cached;
  const formatter = new Intl.DateTimeFormat(locale, optionsByStyle[style]);
  formatters.set(key, formatter);
  return formatter;
}

function format(value: DateValue, style: DateTimeStyle): string {
  if (value === null || value === undefined || value === '') return '-';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return getFormatter(style).format(date);
}

export function formatDateTime(value: DateValue): string {
  return format(value, 'full');
}

export function formatDateTimeMinute(value: DateValue): string {
  return format(value, 'minute');
}

export function formatMonthDay(value: DateValue): string {
  return format(value, 'monthDay');
}

export function formatHourMinute(value: DateValue): string {
  return format(value, 'hourMinute');
}
