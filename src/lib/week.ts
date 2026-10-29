const MONDAY_FIRST_LABELS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

export function startOfWeekMs(now: number): number {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  const day = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - day);
  return d.getTime();
}

export function localDayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

export type DayBar = { label: string; value: number };

export function dailyDurationBars(
  daily: { day: string; durationSec: number }[],
  days: number,
  now: number,
): DayBar[] {
  const byDay = new Map(daily.map((d) => [d.day, d.durationSec]));
  const today = new Date(now);
  today.setHours(0, 0, 0, 0);
  const out: DayBar[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    out.push({
      label: MONDAY_FIRST_LABELS[(d.getDay() + 6) % 7],
      value: byDay.get(localDayKey(d)) ?? 0,
    });
  }
  return out;
}
