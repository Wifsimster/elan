import { useEffect, useState } from 'react';
import { Alert, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { SettingCardHeader } from '@/components/setting-card-header';
import { Type } from '@/constants/theme';
import { applyNotifications } from '@/lib/notifications';
import {
  DEFAULT_WEEK_PLAN,
  getEffectiveWeekPlan,
  resetCustomWeekPlan,
  saveCustomWeekPlan,
  templateById,
  type PlannedSession,
  type WorkoutTemplate,
} from '@/lib/program';
import { useTheme } from '@/hooks/use-theme';

// Libellés courts pour chaque jour de la semaine, lundi en tête (index 0 = lundi).
const WEEK_DAY_LABELS = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];

/** Option proposée pour un jour : repos, vélo, ou muscu avec un template précis. */
type WeekPlanOption =
  | { kind: 'repos'; label: string }
  | { kind: 'velo'; label: string }
  | { kind: 'muscu'; label: string; templateId: WorkoutTemplate['id'] };

const WEEK_PLAN_OPTIONS: WeekPlanOption[] = [
  { kind: 'repos', label: 'Repos' },
  { kind: 'velo', label: 'Vélo' },
  { kind: 'muscu', label: 'Muscu A', templateId: 'fullbody-a' },
  { kind: 'muscu', label: 'Muscu B', templateId: 'fullbody-b' },
  { kind: 'muscu', label: 'Dos', templateId: 'dos-lombaire' },
  { kind: 'muscu', label: 'Cervicales', templateId: 'cervicales' },
];

/** Convertit une option d'UI en `PlannedSession` à persister. */
function optionToPlanned(opt: WeekPlanOption): PlannedSession {
  if (opt.kind === 'repos') return { kind: 'repos' };
  if (opt.kind === 'velo') return { kind: 'velo', label: 'Vélo' };
  return {
    kind: 'muscu',
    label: templateById(opt.templateId)?.name ?? opt.label,
    templateId: opt.templateId,
  };
}

/** Détermine si une option correspond à la séance planifiée pour ce jour. */
function isOptionActive(opt: WeekPlanOption, entry: PlannedSession): boolean {
  if (opt.kind !== entry.kind) return false;
  if (opt.kind === 'muscu' && entry.kind === 'muscu') {
    return opt.templateId === entry.templateId;
  }
  return true;
}

/** Carte Réglages : planning hebdomadaire personnalisable (séance prévue par jour). */
export function WeekPlanCard() {
  const theme = useTheme();
  const [plan, setPlan] = useState<PlannedSession[]>(DEFAULT_WEEK_PLAN);

  useEffect(() => {
    getEffectiveWeekPlan().then(setPlan);
  }, []);

  const updateDay = (dayIndex: number, opt: WeekPlanOption) => {
    setPlan((prev) => {
      const next = prev.slice();
      next[dayIndex] = optionToPlanned(opt);
      saveCustomWeekPlan(next).then(() => applyNotifications());
      return next;
    });
  };

  const reset = () => {
    Alert.alert(
      'Réinitialiser le planning ?',
      'Le planning par défaut sera restauré (Vélo lundi, Full-body A mardi, Full-body B vendredi, repos les autres jours).',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Réinitialiser',
          style: 'destructive',
          onPress: async () => {
            await resetCustomWeekPlan();
            setPlan(DEFAULT_WEEK_PLAN);
            await applyNotifications();
          },
        },
      ],
    );
  };

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="calendar-week" color={theme.accent} title="Planning hebdomadaire" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Choisis la séance prévue pour chaque jour. L'accueil propose la séance du jour selon ce planning."}
      </Text>

      {WEEK_DAY_LABELS.map((dayLabel, i) => (
        <View
          key={dayLabel}
          style={{
            gap: 8,
            paddingTop: i === 0 ? 0 : 10,
            borderTopWidth: i === 0 ? 0 : 1,
            borderTopColor: theme.hairline,
          }}>
          <Text style={{ ...Type.label, color: theme.textSecondary }}>{dayLabel}</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {WEEK_PLAN_OPTIONS.map((opt) => {
              const active = isOptionActive(opt, plan[i]);
              const color =
                opt.kind === 'velo'
                  ? theme.velo
                  : opt.kind === 'muscu'
                    ? theme.muscu
                    : theme.textSecondary;
              return (
                <Chip
                  key={opt.label}
                  label={opt.label}
                  selected={active}
                  color={color}
                  onPress={() => updateDay(i, opt)}
                />
              );
            })}
          </View>
        </View>
      ))}

      <Button
        title="Réinitialiser le planning"
        icon="restore"
        variant="secondary"
        color={theme.accent}
        onPress={reset}
      />
    </Card>
  );
}
