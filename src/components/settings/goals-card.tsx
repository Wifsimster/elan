import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { PressableScale } from '@/components/pressable-scale';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingStepper } from '@/components/settings/setting-stepper';
import { Type } from '@/constants/theme';
import {
  describeGoal,
  loadGoals,
  makeGoal,
  removeGoal,
  saveGoals,
  upsertGoal,
  type Goal,
  type GoalActivity,
  type GoalMetric,
  type GoalPeriod,
} from '@/lib/goals';
import { useTheme } from '@/hooks/use-theme';

const METRIC_OPTIONS: { value: GoalMetric; label: string }[] = [
  { value: 'sessions', label: 'Séances' },
  { value: 'distance', label: 'Distance' },
  { value: 'tonnage', label: 'Tonnage' },
];

const ACTIVITY_OPTIONS: { value: GoalActivity; label: string }[] = [
  { value: 'all', label: 'Toutes' },
  { value: 'velo', label: 'Vélo' },
  { value: 'muscu', label: 'Muscu' },
];

const PERIOD_OPTIONS: { value: GoalPeriod; label: string }[] = [
  { value: 'week', label: 'Semaine' },
  { value: 'month', label: 'Mois' },
];

/** Bornes et pas du sélecteur de cible selon la métrique. */
const TARGET_SPEC: Record<GoalMetric, { unit: string; step: number; min: number; max: number; def: number }> = {
  sessions: { unit: '×', step: 1, min: 1, max: 30, def: 3 },
  distance: { unit: 'km', step: 5, min: 5, max: 1000, def: 100 },
  tonnage: { unit: 'kg', step: 100, min: 100, max: 100_000, def: 5000 },
};

/**
 * Carte Réglages : définition des objectifs d'entraînement (création / suppression).
 * 100 % local — les définitions vivent dans la table `settings` (incluses dans
 * la sauvegarde S3), la progression est calculée à la volée et affichée sur l'Accueil.
 */
export function GoalsCard() {
  const theme = useTheme();
  const [goals, setGoals] = useState<Goal[]>([]);

  const [metric, setMetric] = useState<GoalMetric>('sessions');
  const [activity, setActivity] = useState<GoalActivity>('all');
  const [period, setPeriod] = useState<GoalPeriod>('week');
  const [target, setTarget] = useState(TARGET_SPEC.sessions.def);

  useEffect(() => {
    loadGoals().then(setGoals);
  }, []);

  // Change de métrique : réinitialise la cible sur une valeur sensée pour l'unité.
  const pickMetric = (m: GoalMetric) => {
    setMetric(m);
    setTarget(TARGET_SPEC[m].def);
  };

  const persist = (next: Goal[]) => {
    setGoals(next);
    saveGoals(next);
  };

  const add = () => {
    const goal = makeGoal({ metric, period, target, activity });
    persist(upsertGoal(goals, goal));
  };

  const spec = TARGET_SPEC[metric];

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="target" color={theme.accent} title="Objectifs" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {'Fixe des objectifs simples (séances, distance, tonnage) par semaine ou par mois. Leur progression s’affiche sur l’accueil. 100 % local.'}
      </Text>

      {/* Objectifs définis */}
      {goals.map((g) => (
        <View
          key={g.id}
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            paddingVertical: 8,
            borderTopWidth: 1,
            borderTopColor: theme.hairline,
          }}>
          <MaterialCommunityIcons name="flag-outline" size={18} color={theme.accent} />
          <Text style={{ color: theme.text, flex: 1, fontWeight: '600', fontSize: 14 }}>
            {describeGoal(g)}
          </Text>
          <PressableScale
            onPress={() => persist(removeGoal(goals, g.id))}
            haptic="selection"
            hitSlop={12}
            accessibilityLabel={`Supprimer l'objectif ${describeGoal(g)}`}>
            <MaterialCommunityIcons name="trash-can-outline" size={20} color={theme.textSecondary} />
          </PressableScale>
        </View>
      ))}

      {/* Formulaire d'ajout */}
      <View
        style={{
          gap: 10,
          paddingTop: 12,
          borderTopWidth: 1,
          borderTopColor: theme.hairline,
        }}>
        <Text style={{ ...Type.label, color: theme.textSecondary }}>Nouvel objectif</Text>

        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {METRIC_OPTIONS.map((o) => (
            <Chip
              key={o.value}
              label={o.label}
              selected={metric === o.value}
              color={theme.accent}
              onPress={() => pickMetric(o.value)}
            />
          ))}
        </View>

        {/* Type d'activité : pertinent uniquement pour un objectif de séances. */}
        {metric === 'sessions' ? (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {ACTIVITY_OPTIONS.map((o) => (
              <Chip
                key={o.value}
                label={o.label}
                selected={activity === o.value}
                color={theme.muscu}
                onPress={() => setActivity(o.value)}
              />
            ))}
          </View>
        ) : null}

        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {PERIOD_OPTIONS.map((o) => (
            <Chip
              key={o.value}
              label={o.label}
              selected={period === o.value}
              color={theme.velo}
              onPress={() => setPeriod(o.value)}
            />
          ))}
        </View>

        <SettingStepper
          label="Cible"
          value={target}
          unit={spec.unit}
          step={spec.step}
          min={spec.min}
          max={spec.max}
          onChange={setTarget}
        />

        <Button title="Ajouter l’objectif" icon="plus" color={theme.accent} onPress={add} />
      </View>
    </Card>
  );
}
