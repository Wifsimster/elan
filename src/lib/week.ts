// Aides temporelles « semaine » pour l'accueil : début de semaine (lundi) et
// agrégation des durées quotidiennes en barres alignées sur les N derniers
// jours. Pur (aucune dépendance React/Expo) — testé dans __tests__/lib/week.test.ts.

// Initiales des jours, lundi en tête (index 0 = lundi).
const MONDAY_FIRST_LABELS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Minuit (heure locale) du lundi de la semaine contenant `now` (ms epoch). */
export function startOfWeekMs(now: number): number {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  const day = (d.getDay() + 6) % 7; // lundi = 0
  d.setDate(d.getDate() - day);
  return d.getTime();
}

/**
 * Clé locale `YYYY-MM-DD` d'une date — même formulation que
 * `date(..., 'localtime')` côté SQLite (cf. `dailyDurations`), pour apparier les
 * lignes renvoyées par la base.
 */
export function localDayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

/** Une barre du graphe d'activité : initiale du jour + durée (s). */
export type DayBar = { label: string; value: number };

/**
 * Construit les barres des `days` derniers jours (aujourd'hui à droite) à partir
 * des durées quotidiennes (`{ day: 'YYYY-MM-DD', durationSec }`). Le libellé est
 * l'initiale du jour (lundi en tête) ; les jours sans donnée valent 0.
 */
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
