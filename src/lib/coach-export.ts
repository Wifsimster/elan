import {
  exerciseHistory,
  exportAll,
  getMuscuSets,
  getProfile,
  listBodyMeasurements,
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
  formatPace,
  formatSpeed,
  formatDateTime,
  formatDateShort,
} from '@/lib/format';
import { ACTIVITY_META, isGpsActivity, usesPace } from '@/lib/activity';
import { goalLabel } from '@/lib/exercises';
import { difficultyLabel } from '@/lib/progression-advice';
import {
  describeGoal,
  formatGoalValue,
  goalProgressList,
  type GoalProgress,
} from '@/lib/goals';
import { TEMPLATES, WEEK_PLAN, targetHint } from '@/lib/program';
import { nowMs } from '@/lib/time';
import type { BodyMeasurement, MuscuSet, Profile, Session } from '@/lib/types';

const DAY = 86_400_000;
const JOURS_SEMAINE = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];

function num(n: number | null | undefined, digits = 1): string {
  if (n == null) return '—';
  const r = Math.round(n * 10 ** digits) / 10 ** digits;
  return (Number.isInteger(r) ? `${r}` : r.toFixed(digits)).replace('.', ',');
}

function cell(s: string): string {
  return s.replace(/\|/g, '\\|').replace(/\n+/g, ' ').trim();
}

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

function profileSection(profile: Profile): string {
  const sexLabel = profile.sex === 'h' ? 'homme' : profile.sex === 'f' ? 'femme' : 'non précisé';
  return [
    '## Profil',
    '',
    `- Poids : ${num(profile.weightKg)} kg`,
    `- Taille : ${profile.heightCm} cm`,
    `- FC max : ${profile.maxHr} bpm`,
    `- Objectif : ${goalLabel(profile.goal)}`,
    `- Sexe : ${sexLabel}`,
  ].join('\n');
}

function weightSection(measurements: BodyMeasurement[]): string | null {
  if (measurements.length === 0) return null;
  const lines: string[] = [
    '## Poids corporel',
    '',
    'Journal des pesées, du plus ancien au plus récent. La dernière pesée est le poids de référence du profil.',
    '',
    '| Date | Poids (kg) |',
    '| --- | --- |',
  ];
  for (const m of [...measurements].reverse()) {
    lines.push(`| ${formatDateShort(m.measuredAt)} | ${num(m.weightKg)} |`);
  }
  return lines.join('\n');
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
      p.kind === 'repos' ? 'Repos' : `${ACTIVITY_META[p.kind].label} — ${p.label}`;
    lines.push(`- ${JOURS_SEMAINE[i]} : ${label}`);
  }

  for (const t of TEMPLATES) {
    lines.push('', `### ${t.name} (${t.day})`, '');
    for (let i = 0; i < t.exercises.length; i++) {
      const ex = t.exercises[i];
      const charge = ex.startWeightKg > 0 ? `${num(ex.startWeightKg)} kg` : 'poids du corps';
      lines.push(
        `${i + 1}. **${ex.name}** — ${targetHint(ex)}, charge de départ ${charge}`,
        `   _Muscles :_ ${ex.muscles.join(', ')}`,
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

function goalsSection(progress: GoalProgress[]): string | null {
  if (progress.length === 0) return null;
  const lines: string[] = [
    '## Objectifs',
    '',
    'Avancement sur la période courante (semaine ou mois selon l’objectif).',
    '',
    '| Objectif | Réalisé | Cible | Atteint |',
    '| --- | --- | --- | --- |',
  ];
  for (const p of progress) {
    lines.push(
      `| ${cell(describeGoal(p.goal))} | ${formatGoalValue(p.goal, p.value)} | ${formatGoalValue(
        p.goal,
        p.target,
      )} | ${p.done ? 'oui' : `${Math.round(p.ratio * 100)} %`} |`,
    );
  }
  return lines.join('\n');
}

function progressionSection(history: { exercise: string; points: ExercisePoint[] }[]): string {
  if (history.length === 0) {
    return '## Progression par exercice (musculation)\n\n_Aucune séance de musculation enregistrée._';
  }
  const lines: string[] = [
    '## Progression par exercice (musculation)',
    '',
    'Une ligne par séance, du plus ancien au plus récent. « Charge max » = série la plus lourde de la séance ; « Reps » = répétitions de cette série ; « Volume » = somme de (reps × charge) sur toutes les séries ; « Ressenti » = effort noté par l\'utilisateur (facile / moyen / dur) pour décider d\'augmenter ou non les reps et la charge.',
  ];
  for (const h of history) {
    lines.push('', `### ${h.exercise}`, '');
    lines.push(
      '| Date | Charge max (kg) | Reps | Volume | Séries | Ressenti |',
      '| --- | --- | --- | --- | --- | --- |',
    );
    for (const p of h.points) {
      lines.push(
        `| ${formatDateShort(p.startedAt)} | ${num(p.maxWeightKg)} | ${p.topReps} | ${num(
          p.volume,
        )} | ${p.sets} | ${p.difficulty ? difficultyLabel(p.difficulty) : '—'} |`,
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

function gpsSection(sessions: Session[]): string {
  if (sessions.length === 0) {
    return '## Historique des sorties\n\n_Aucune sortie enregistrée._';
  }
  const lines: string[] = [
    '## Historique des sorties',
    '',
    '| Date | Activité | Durée | Distance | Moy. | Max | FC moy | D+ | Calories | Source |',
    '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
  ];
  for (const s of sessions) {
    const dplus = s.elevationGainM != null ? `${Math.round(s.elevationGainM)} m` : '—';
    const speed = usesPace(s.type) ? formatPace : formatSpeed;
    lines.push(
      `| ${formatDateTime(s.startedAt)} | ${ACTIVITY_META[s.type].label} | ${formatDuration(
        s.movingTimeSec ?? s.durationSec,
      )} | ${formatDistance(s.distanceM)} | ${speed(s.avgSpeedKmh)} | ${speed(
        s.maxSpeedKmh,
      )} | ${formatHr(s.avgHr)} | ${dplus} | ${formatCalories(s.calories)} | ${s.source ?? 'app'} |`,
    );
  }
  return lines.join('\n');
}

export async function buildCoachMarkdown(): Promise<string> {
  const [profile, sessions, measurements, goals] = await Promise.all([
    getProfile(),
    listSessions(10_000),
    listBodyMeasurements(),
    goalProgressList(),
  ]);

  const muscuSessions = sessions.filter((s) => s.type === 'muscu');
  const gpsSessions = sessions.filter((s) => isGpsActivity(s.type));

  const muscuDetail = await Promise.all(
    muscuSessions.map(async (session) => ({ session, sets: await getMuscuSets(session.id) })),
  );

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
    profileSection(profile),
    weightSection(measurements),
    programSection(),
    await statsSection(),
    goalsSection(goals),
    progressionSection(history),
    muscuDetailSection(muscuDetail),
    gpsSection(gpsSessions),
  ]
    .filter((s): s is string => s != null)
    .join('\n\n');
}

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
