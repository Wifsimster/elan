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
  // On arrondit à la minute la plus proche d'abord, puis on dérive heures et
  // minutes : sinon arrondir les minutes seules pouvait produire « 1 h 60 »
  // (ex. 1 h 59 min 30 s → 60 min).
  const totalMin = Math.round(s / 60);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h > 0) return `${h} h ${m.toString().padStart(2, '0')}`;
  return `${m} min`;
}

/**
 * Mesure scindée en chiffre et unité, pour l'afficher dans une `StatTile`
 * (grand nombre + unité en petit sur la ligne de base, sans retour à la ligne
 * disgracieux). `unit` est absente quand la valeur est inconnue (« — »).
 */
export type Measure = { value: string; unit?: string };

/** Recompose une `Measure` en chaîne « valeur unité » (« 12,4 km »). */
function joinMeasure(m: Measure): string {
  return m.unit ? `${m.value} ${m.unit}` : m.value;
}

/** Décimale française : 12.4 -> "12,4". */
const decimalFr = (n: number, digits = 1) => n.toFixed(digits).replace('.', ',');

/** Mètres -> { "12,4", "km" } ou { "850", "m" }. */
export function distanceParts(meters: number | null | undefined): Measure {
  if (meters == null) return { value: '—' };
  if (meters >= 1000) return { value: decimalFr(meters / 1000), unit: 'km' };
  return { value: String(Math.round(meters)), unit: 'm' };
}

export function speedParts(kmh: number | null | undefined): Measure {
  if (kmh == null) return { value: '—' };
  return { value: decimalFr(kmh), unit: 'km/h' };
}

export function hrParts(bpm: number | null | undefined): Measure {
  if (bpm == null) return { value: '—' };
  return { value: String(Math.round(bpm)), unit: 'bpm' };
}

export function caloriesParts(kcal: number | null | undefined): Measure {
  if (kcal == null) return { value: '—' };
  return { value: String(Math.round(kcal)), unit: 'kcal' };
}

/** Mètres -> { "57", "m" } (dénivelé, jamais converti en km). */
export function elevationParts(meters: number | null | undefined): Measure {
  return { value: String(Math.round(meters ?? 0)), unit: 'm' };
}

/** Tours/min -> { "84", "tr/min" } (cadence). */
export function cadenceParts(rpm: number | null | undefined): Measure {
  if (rpm == null) return { value: '—' };
  return { value: String(Math.round(rpm)), unit: 'tr/min' };
}

/** Mètres -> "12,4 km" ou "850 m". */
export function formatDistance(meters: number | null | undefined): string {
  return joinMeasure(distanceParts(meters));
}

export function formatSpeed(kmh: number | null | undefined): string {
  return joinMeasure(speedParts(kmh));
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
