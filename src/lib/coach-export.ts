// Export des données vers un format lisible par une IA (« coach »).
// Deux artefacts, 100 % hors-ligne :
//   - un bilan Markdown : programme, planning, stats, progression et historique,
//     pensé pour être déposé dans un projet Claude Code qui suit la programmation ;
//   - un export JSON brut : profil + programme + instantané complet de la base.
import {
  exerciseHistory,
  exportAll,
  getMuscuSets,
  getProfile,
  listSessions,
  statsSince,
  type ExercisePoint,
} from '@/lib/db';
import {
  formatCalories,
  formatDistance,
  formatDuration,
  formatDurationShort,
  formatHr,
  formatSpeed,
  formatDateTime,
  formatDateShort,
} from '@/lib/format';
import { TEMPLATES, WEEK_PLAN, targetHint } from '@/lib/program';
import { nowMs } from '@/lib/time';
import type { MuscuSet, Session } from '@/lib/types';

const DAY = 86_400_000;
const JOURS_SEMAINE = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];

/** Nombre en français, sans décimale superflue (3 -> « 3 », 12.5 -> « 12,5 »). */
function num(n: number | null | undefined, digits = 1): string {
  if (n == null) return '—';
  const r = Math.round(n * 10 ** digits) / 10 ** digits;
  return (Number.isInteger(r) ? `${r}` : r.toFixed(digits)).replace('.', ',');
}

/** Échappe le caractère pipe pour ne pas casser les tableaux Markdown. */
function cell(s: string): string {
  return s.replace(/\|/g, '\\|').replace(/\n+/g, ' ').trim();
}

/** Regroupe les séries d'une séance par exercice, dans l'ordre d'apparition. */
function groupByExercise(sets: MuscuSet[]): { exercise: string; sets: MuscuSet[] }[] {
  const order: string[] = [];
  const byEx = new Map<string, MuscuSet[]>();
  for (const s of sets) {
    if (!byEx.has(s.exercise)) {
      byEx.set(s.exercise, []);
      order.push(s.exercise);
    }
    byEx.get(s.exercise)!.push(s);
  }
  return order.map((exercise) => ({ exercise, sets: byEx.get(exercise)! }));
}

function profileSection(weightKg: number, maxHr: number): string {
  return ['## Profil', '', `- Poids : ${num(weightKg)} kg`, `- FC max : ${maxHr} bpm`].join('\n');
}

function programSection(): string {
  const lines: string[] = ['## Mon programme'];
  lines.push(
    '',
    'Programme « Maison (haltères) » orienté prise de muscle : deux séances full-body par semaine, la charge est le moteur de la progression (surcharge progressive).',
    '',
    '### Planning hebdomadaire',
    '',
  );
  for (let i = 0; i < WEEK_PLAN.length; i++) {
    const p = WEEK_PLAN[i];
    const label =
      p.kind === 'repos'
        ? 'Repos'
        : p.kind === 'velo'
          ? `Vélo — ${p.label}`
          : `Musculation — ${p.label}`;
    lines.push(`- ${JOURS_SEMAINE[i]} : ${label}`);
  }

  for (const t of TEMPLATES) {
    lines.push('', `### ${t.name} (${t.day})`, '');
    for (let i = 0; i < t.exercises.length; i++) {
      const ex = t.exercises[i];
      const charge = ex.startWeightKg > 0 ? `${num(ex.startWeightKg)} kg` : 'poids du corps';
      lines.push(
        `${i + 1}. **${ex.name}** — ${targetHint(ex)}, charge de départ ${charge}`,
        `   _Exécution :_ ${ex.howTo}`,
      );
    }
  }
  return lines.join('\n');
}

async function statsSection(): Promise<string> {
  const now = nowMs();
  const periods: { label: string; since: number }[] = [
    { label: '7 derniers jours', since: now - 7 * DAY },
    { label: '30 derniers jours', since: now - 30 * DAY },
    { label: '90 derniers jours', since: now - 90 * DAY },
    { label: 'Depuis le début', since: 0 },
  ];
  const rows = await Promise.all(
    periods.map(async (p) => {
      const s = await statsSince(p.since);
      return `| ${p.label} | ${s.sessionCount} | ${formatDurationShort(
        s.totalDurationSec,
      )} | ${formatDistance(s.totalDistanceM)} | ${formatCalories(s.totalCalories)} |`;
    }),
  );
  return [
    '## Statistiques',
    '',
    '| Période | Séances | Durée totale | Distance (vélo) | Calories |',
    '| --- | --- | --- | --- | --- |',
    ...rows,
  ].join('\n');
}

function progressionSection(history: { exercise: string; points: ExercisePoint[] }[]): string {
  if (history.length === 0) {
    return '## Progression par exercice (musculation)\n\n_Aucune séance de musculation enregistrée._';
  }
  const lines: string[] = [
    '## Progression par exercice (musculation)',
    '',
    'Une ligne par séance, du plus ancien au plus récent. « Charge max » = série la plus lourde de la séance ; « Reps » = répétitions de cette série ; « Volume » = somme de (reps × charge) sur toutes les séries.',
  ];
  for (const h of history) {
    lines.push('', `### ${h.exercise}`, '');
    lines.push('| Date | Charge max (kg) | Reps | Volume | Séries |', '| --- | --- | --- | --- | --- |');
    for (const p of h.points) {
      lines.push(
        `| ${formatDateShort(p.startedAt)} | ${num(p.maxWeightKg)} | ${p.topReps} | ${num(
          p.volume,
        )} | ${p.sets} |`,
      );
    }
  }
  return lines.join('\n');
}

function muscuDetailSection(
  sessions: { session: Session; sets: MuscuSet[] }[],
): string {
  if (sessions.length === 0) {
    return '## Détail des séances de musculation\n\n_Aucune séance de musculation enregistrée._';
  }
  const lines: string[] = [
    '## Détail des séances de musculation',
    '',
    'Toutes les séries enregistrées, de la plus récente à la plus ancienne.',
  ];
  for (const { session, sets } of sessions) {
    lines.push(
      '',
      `### ${formatDateTime(session.startedAt)} — durée ${formatDuration(session.durationSec)}`,
    );
    if (session.avgHr != null) lines.push(`FC moyenne ${formatHr(session.avgHr)} · max ${formatHr(session.maxHr)}.`);
    if (session.notes) lines.push(`Notes : ${session.notes}`);
    lines.push('', '| Exercice | Série | Reps | Charge (kg) |', '| --- | --- | --- | --- |');
    for (const group of groupByExercise(sets)) {
      for (let i = 0; i < group.sets.length; i++) {
        const s = group.sets[i];
        lines.push(`| ${cell(group.exercise)} | ${i + 1} | ${s.reps} | ${num(s.weightKg)} |`);
      }
    }
  }
  return lines.join('\n');
}

function veloSection(sessions: Session[]): string {
  if (sessions.length === 0) {
    return '## Historique des sorties vélo\n\n_Aucune sortie vélo enregistrée._';
  }
  const lines: string[] = [
    '## Historique des sorties vélo',
    '',
    '| Date | Durée | Distance | Vit. moy | Vit. max | FC moy | D+ | Calories | Source |',
    '| --- | --- | --- | --- | --- | --- | --- | --- | --- |',
  ];
  for (const s of sessions) {
    const dplus = s.elevationGainM != null ? `${Math.round(s.elevationGainM)} m` : '—';
    lines.push(
      `| ${formatDateTime(s.startedAt)} | ${formatDuration(s.durationSec)} | ${formatDistance(
        s.distanceM,
      )} | ${formatSpeed(s.avgSpeedKmh)} | ${formatSpeed(s.maxSpeedKmh)} | ${formatHr(
        s.avgHr,
      )} | ${dplus} | ${formatCalories(s.calories)} | ${s.source ?? 'app'} |`,
    );
  }
  return lines.join('\n');
}

/**
 * Construit le bilan Markdown complet : un seul document auto-suffisant à
 * déposer dans un projet pour qu'une IA suive la programmation et coache.
 */
export async function buildCoachMarkdown(): Promise<string> {
  const [profile, sessions] = await Promise.all([getProfile(), listSessions(10_000)]);

  const muscuSessions = sessions.filter((s) => s.type === 'muscu');
  const veloSessions = sessions.filter((s) => s.type === 'velo');

  // Séries par séance muscu (déjà triées de la plus récente à la plus ancienne).
  const muscuDetail = await Promise.all(
    muscuSessions.map(async (session) => ({ session, sets: await getMuscuSets(session.id) })),
  );

  // Progression par exercice : on prend la liste des exercices distincts vus
  // dans l'historique muscu, puis leur courbe complète.
  const exerciseNames: string[] = [];
  for (const { sets } of muscuDetail) {
    for (const s of sets) if (!exerciseNames.includes(s.exercise)) exerciseNames.push(s.exercise);
  }
  const history = await Promise.all(
    exerciseNames.map(async (exercise) => ({ exercise, points: await exerciseHistory(exercise) })),
  );

  const header = [
    '# Élan — Export pour ton coach IA',
    '',
    `Généré le ${formatDateTime(nowMs())}. Données issues de l'app Élan (toutes locales).`,
    'Charges en kg, durées en h/min, distances en km. Dates en heure locale.',
  ].join('\n');

  return [
    header,
    profileSection(profile.weightKg, profile.maxHr),
    programSection(),
    await statsSection(),
    progressionSection(history),
    muscuDetailSection(muscuDetail),
    veloSection(veloSessions),
  ].join('\n\n');
}

/**
 * Export JSON brut : profil + programme + instantané complet de la base
 * (séances, points GPS, séries, réglages non secrets). Pour réimport/analyse.
 */
export async function buildCoachJson(): Promise<string> {
  const [profile, data] = await Promise.all([getProfile(), exportAll()]);
  return JSON.stringify(
    {
      format: 1,
      app: 'suivi-sport',
      exportedAt: nowMs(),
      profile,
      program: { templates: TEMPLATES, weekPlan: WEEK_PLAN },
      data,
    },
    null,
    2,
  );
}
