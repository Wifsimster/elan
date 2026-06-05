// Helpers de formatage (FR).

/** 3725 -> "1:02:05" ; 125 -> "2:05". */
export function formatDuration(totalSec: number): string {
  const s = Math.max(0, Math.floor(totalSec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = m.toString().padStart(2, '0');
  const ss = sec.toString().padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

/** Durée compacte pour les listes : "1 h 02", "45 min". */
export function formatDurationShort(totalSec: number): string {
  const s = Math.max(0, Math.floor(totalSec));
  const h = Math.floor(s / 3600);
  const m = Math.round((s % 3600) / 60);
  if (h > 0) return `${h} h ${m.toString().padStart(2, '0')}`;
  return `${m} min`;
}

/** Mètres -> "12,4 km" ou "850 m". */
export function formatDistance(meters: number | null | undefined): string {
  if (meters == null) return '—';
  if (meters >= 1000) return `${(meters / 1000).toFixed(1).replace('.', ',')} km`;
  return `${Math.round(meters)} m`;
}

export function formatSpeed(kmh: number | null | undefined): string {
  if (kmh == null) return '—';
  return `${kmh.toFixed(1).replace('.', ',')} km/h`;
}

export function formatHr(bpm: number | null | undefined): string {
  if (bpm == null) return '—';
  return `${Math.round(bpm)} bpm`;
}

export function formatCalories(kcal: number | null | undefined): string {
  if (kcal == null) return '—';
  return `${Math.round(kcal)} kcal`;
}

const JOURS = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'];
const MOIS = [
  'janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
  'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.',
];

/** "lun. 2 juin, 18:30". */
export function formatDateTime(ms: number): string {
  const d = new Date(ms);
  const heure = `${d.getHours().toString().padStart(2, '0')}:${d
    .getMinutes()
    .toString()
    .padStart(2, '0')}`;
  return `${JOURS[d.getDay()]} ${d.getDate()} ${MOIS[d.getMonth()]}, ${heure}`;
}

export function formatDateShort(ms: number): string {
  const d = new Date(ms);
  return `${d.getDate()} ${MOIS[d.getMonth()]}`;
}

/** « aujourd'hui », « hier », « il y a 4 jours » — relatif au début de journée. */
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
