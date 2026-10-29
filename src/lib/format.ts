export function formatDuration(totalSec: number): string {
  const s = Math.max(0, Math.floor(totalSec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = m.toString().padStart(2, '0');
  const ss = sec.toString().padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

export function formatDurationShort(totalSec: number): string {
  const s = Math.max(0, Math.floor(totalSec));
  const totalMin = Math.round(s / 60);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h > 0) return `${h} h ${m.toString().padStart(2, '0')}`;
  return `${m} min`;
}

export type Measure = { value: string; unit?: string };

function joinMeasure(m: Measure): string {
  return m.unit ? `${m.value} ${m.unit}` : m.value;
}

const decimalFr = (n: number, digits = 1) => n.toFixed(digits).replace('.', ',');

export function distanceParts(meters: number | null | undefined): Measure {
  if (meters == null) return { value: '—' };
  if (meters >= 1000) return { value: decimalFr(meters / 1000), unit: 'km' };
  return { value: String(Math.round(meters)), unit: 'm' };
}

export function speedParts(kmh: number | null | undefined): Measure {
  if (kmh == null) return { value: '—' };
  return { value: decimalFr(kmh), unit: 'km/h' };
}

export function paceParts(kmh: number | null | undefined): Measure {
  if (kmh == null || kmh < 1) return { value: '—' };
  const secPerKm = Math.round(3600 / kmh);
  if (secPerKm >= 3600) return { value: '—' };
  const min = Math.floor(secPerKm / 60);
  const sec = secPerKm % 60;
  return { value: `${min}:${sec.toString().padStart(2, '0')}`, unit: '/km' };
}

export function hrParts(bpm: number | null | undefined): Measure {
  if (bpm == null) return { value: '—' };
  return { value: String(Math.round(bpm)), unit: 'bpm' };
}

export function caloriesParts(kcal: number | null | undefined): Measure {
  if (kcal == null) return { value: '—' };
  return { value: String(Math.round(kcal)), unit: 'kcal' };
}

export function elevationParts(meters: number | null | undefined): Measure {
  return { value: String(Math.round(meters ?? 0)), unit: 'm' };
}

export function cadenceParts(rpm: number | null | undefined): Measure {
  if (rpm == null) return { value: '—' };
  return { value: String(Math.round(rpm)), unit: 'tr/min' };
}

export function formatDistance(meters: number | null | undefined): string {
  return joinMeasure(distanceParts(meters));
}

export function formatSpeed(kmh: number | null | undefined): string {
  return joinMeasure(speedParts(kmh));
}

export function formatPace(kmh: number | null | undefined): string {
  return joinMeasure(paceParts(kmh));
}

export function formatHr(bpm: number | null | undefined): string {
  return joinMeasure(hrParts(bpm));
}

export function formatCalories(kcal: number | null | undefined): string {
  return joinMeasure(caloriesParts(kcal));
}

const JOURS = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'];
const MOIS = [
  'janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
  'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.',
];

export function formatDateTime(ms: number, withYear = false): string {
  const d = new Date(ms);
  const heure = `${d.getHours().toString().padStart(2, '0')}:${d
    .getMinutes()
    .toString()
    .padStart(2, '0')}`;
  const annee = withYear ? ` ${d.getFullYear()}` : '';
  return `${JOURS[d.getDay()]} ${d.getDate()} ${MOIS[d.getMonth()]}${annee}, ${heure}`;
}

export function formatDateShort(ms: number): string {
  const d = new Date(ms);
  return `${d.getDate()} ${MOIS[d.getMonth()]}`;
}

export function formatRelativeDays(ms: number): string {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffDays = Math.round((today.getTime() - d.getTime()) / 86_400_000);
  if (diffDays <= 0) return "aujourd'hui";
  if (diffDays === 1) return 'hier';
  return `il y a ${diffDays} jours`;
}
