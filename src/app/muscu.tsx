import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { PressableScale } from '@/components/pressable-scale';
import { Elevation, Radius, Type } from '@/constants/theme';
import { autoBackup } from '@/lib/backup';
import { estimateCalories } from '@/lib/calories';
import {
  createSession,
  getProfile,
  lastWeightByExercise,
  replaceMuscuSets,
  updateSession,
} from '@/lib/db';
import { formatDuration } from '@/lib/format';
import { TEMPLATES, targetHint, defaultReps, templateById, type WorkoutTemplate } from '@/lib/program';
import { nowMs } from '@/lib/time';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useStopwatch } from '@/hooks/use-stopwatch';
import { useTheme } from '@/hooks/use-theme';

type SetRow = { reps: number; weightKg: number; done?: boolean };
type Exercise = {
  id: string;
  name: string;
  sets: SetRow[];
  /** Cible issue d'un programme, ex. « 3 × 8-12 / bras » (absente en saisie libre). */
  target?: string;
  /** Unité des reps : « sec » pour le gainage chronométré, « reps » sinon. */
  repUnit?: string;
  /** Dernière charge enregistrée pour cet exercice (amorce de progression). */
  lastWeight?: number;
  /** Explication « comment faire », dépliable depuis la carte. */
  howTo?: string;
};
type HrSample = { ts: number; hr: number };

const COMMON = [
  'Développé couché',
  'Squat',
  'Soulevé de terre',
  'Développé militaire',
  'Rowing',
  'Tractions',
  'Curl biceps',
  'Dips',
];

let uid = 0;
const nextId = () => `e${uid++}`;

const fmtKg = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

export default function MuscuScreen() {
  useKeepAwake();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();

  const watch = useStopwatch();
  const { bpm, subscribe: subscribeHr } = useHeartRate();
  const { template } = useLocalSearchParams<{ template?: string }>();

  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [openHelp, setOpenHelp] = useState<string | null>(null);

  const startedAtRef = useRef<number>(0);
  const hrSamplesRef = useRef<HrSample[]>([]);
  const weightRef = useRef<number>(70);
  const maxHrRef = useRef<number>(190);

  const loadTemplate = async (t: WorkoutTemplate) => {
    const last = await lastWeightByExercise(t.exercises.map((e) => e.name));
    setExercises((prev) => [
      ...prev,
      ...t.exercises.map((ex) => {
        const lw = last[ex.name];
        return {
          id: nextId(),
          name: ex.name,
          target: targetHint(ex),
          repUnit: ex.timed ? 'sec' : 'reps',
          howTo: ex.howTo,
          // Le gainage n'a pas de charge : on n'affiche pas de « dernière fois ».
          lastWeight: ex.timed ? undefined : lw,
          sets: Array.from({ length: ex.sets }, () => ({
            reps: defaultReps(ex),
            weightKg: ex.timed ? ex.startWeightKg : (lw ?? ex.startWeightKg),
          })),
        };
      }),
    ]);
  };

  useEffect(() => {
    startedAtRef.current = nowMs();
    watch.start();
    getProfile().then((p) => {
      weightRef.current = p.weightKg;
      maxHrRef.current = p.maxHr;
    });
    const preset = templateById(template);
    // loadTemplate ne pose son état qu'après une lecture DB async (hors rendu).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (preset) loadTemplate(preset); // pré-chargement depuis la « Séance du jour »
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Échantillonnage FC : on s'abonne aux trames BLE brutes pour ne pas perdre
  // les paliers (React déduplique les setStates identiques côté `bpm`).
  useEffect(() => {
    return subscribeHr(({ ts, hr }) => {
      hrSamplesRef.current.push({ ts, hr });
    });
  }, [subscribeHr]);

  const addExercise = (name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setExercises((prev) => [
      ...prev,
      { id: nextId(), name: trimmed, sets: [{ reps: 10, weightKg: 20 }] },
    ]);
    setDraft('');
  };

  const removeExercise = (id: string) =>
    setExercises((prev) => prev.filter((e) => e.id !== id));

  const addSet = (id: string) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id
          ? { ...e, sets: [...e.sets, { ...(e.sets[e.sets.length - 1] ?? { reps: 10, weightKg: 20 }) }] }
          : e,
      ),
    );

  const removeSet = (id: string, idx: number) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id ? { ...e, sets: e.sets.filter((_, i) => i !== idx) } : e,
      ),
    );

  const updateSet = (id: string, idx: number, patch: Partial<SetRow>) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id
          ? {
              ...e,
              sets: e.sets.map((s, i) => (i === idx ? { ...s, ...patch } : s)),
            }
          : e,
      ),
    );

  // Cocher / décocher une série une fois exécutée (suivi en cours de séance).
  const toggleSet = (id: string, idx: number) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id
          ? { ...e, sets: e.sets.map((s, i) => (i === idx ? { ...s, done: !s.done } : s)) }
          : e,
      ),
    );

  const totalSets = exercises.reduce((a, e) => a + e.sets.length, 0);
  const doneSets = exercises.reduce((a, e) => a + e.sets.filter((s) => s.done).length, 0);
  const totalVolume = exercises.reduce(
    (a, e) => a + e.sets.reduce((b, s) => b + s.reps * s.weightKg, 0),
    0,
  );

  const computeHr = () => {
    const samples = hrSamplesRef.current;
    if (samples.length === 0) return { avgHr: null, maxHr: null };
    const sum = samples.reduce((a, s) => a + s.hr, 0);
    const max = samples.reduce((a, s) => Math.max(a, s.hr), 0);
    return { avgHr: Math.round(sum / samples.length), maxHr: max };
  };

  const finish = () => {
    if (totalSets === 0) {
      Alert.alert('Séance vide', 'Ajoutez au moins un exercice avant de terminer.');
      return;
    }
    Alert.alert('Terminer la séance ?', 'La séance sera enregistrée.', [
      { text: 'Continuer', style: 'cancel' },
      { text: 'Terminer', onPress: save },
    ]);
  };

  const save = async () => {
    setSaving(true);
    watch.pause();
    const durationSec = watch.elapsedSec;
    const { avgHr, maxHr } = computeHr();
    const calories = estimateCalories({
      type: 'muscu',
      weightKg: weightRef.current,
      durationSec,
      avgHr,
      maxHr: maxHrRef.current,
    });

    const id = await createSession('muscu', startedAtRef.current);
    await updateSession(id, {
      endedAt: nowMs(),
      durationSec,
      avgHr,
      maxHr,
      calories,
      notes: `${exercises.length} exercices · ${totalSets} séries · ${Math.round(totalVolume)} kg soulevés`,
    });

    const flat = exercises.flatMap((e) =>
      e.sets.map((s, i) => ({
        exercise: e.name,
        setIndex: i + 1,
        reps: s.reps,
        weightKg: s.weightKg,
      })),
    );
    await replaceMuscuSets(id, flat);

    autoBackup(); // sauvegarde homelab best-effort (ne bloque pas la navigation)
    router.replace({ pathname: '/session/[id]', params: { id } });
  };

  const discard = () => {
    if (totalSets === 0) {
      router.back();
      return;
    }
    Alert.alert('Abandonner la séance ?', 'Les données ne seront pas enregistrées.', [
      { text: 'Continuer', style: 'cancel' },
      { text: 'Abandonner', style: 'destructive', onPress: () => router.back() },
    ]);
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingTop: insets.top + 8,
          paddingBottom: insets.bottom + 120,
          paddingHorizontal: 16,
          gap: 14,
        }}>
        {/* En-tête */}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <PressableScale onPress={discard} haptic="selection" scaleTo={0.88} hitSlop={12}>
            <MaterialCommunityIcons name="close" size={26} color={theme.text} />
          </PressableScale>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <MaterialCommunityIcons name="dumbbell" size={22} color={theme.muscu} />
            <Text style={{ ...Type.headline, color: theme.text }}>Musculation</Text>
          </View>
          <View style={{ width: 26 }} />
        </View>

        {/* Résumé */}
        <Card style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
          <Summary label="Durée" value={formatDuration(watch.elapsedSec)} theme={theme} />
          <Summary
            label="Séries"
            value={totalSets > 0 ? `${doneSets}/${totalSets}` : '0'}
            theme={theme}
          />
          <Summary
            label="Cardio"
            value={bpm != null ? `${bpm}` : '—'}
            theme={theme}
            color={theme.heart}
          />
          <Summary
            label="Volume"
            value={`${Math.round(totalVolume)} kg`}
            theme={theme}
            color={theme.muscu}
          />
        </Card>

        {/* Charger un programme */}
        <Card style={{ gap: 10 }}>
          <Text style={{ ...Type.subtitle, color: theme.text }}>Charger un programme</Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {TEMPLATES.map((t) => (
              <PressableScale
                key={t.id}
                onPress={() => loadTemplate(t)}
                haptic="light"
                style={{
                  flex: 1,
                  alignItems: 'center',
                  gap: 2,
                  paddingVertical: 12,
                  borderRadius: Radius.sm,
                  borderWidth: 1,
                  borderColor: theme.muscu,
                  backgroundColor: theme.backgroundElement,
                }}>
                <Text style={{ color: theme.muscu, fontWeight: '800', fontSize: 15 }}>{t.name}</Text>
                <Text style={{ color: theme.textSecondary, fontSize: 11 }}>{t.day}</Text>
              </PressableScale>
            ))}
          </View>
        </Card>

        {/* Exercices */}
        {exercises.map((ex) => (
          <Card key={ex.id} style={{ gap: 10 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1 }}>
                <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
                  {ex.name}
                </Text>
                {ex.target || ex.lastWeight != null ? (
                  <Text style={{ color: theme.textSecondary, fontSize: 12, marginTop: 2 }}>
                    {ex.target ? `cible ${ex.target}` : ''}
                    {ex.target && ex.lastWeight != null ? '  ·  ' : ''}
                    {ex.lastWeight != null ? `dernière fois : ${fmtKg(ex.lastWeight)} kg` : ''}
                  </Text>
                ) : null}
              </View>
              {ex.howTo ? (
                <PressableScale
                  onPress={() => setOpenHelp((cur) => (cur === ex.id ? null : ex.id))}
                  haptic="selection"
                  hitSlop={8}>
                  <MaterialCommunityIcons
                    name={openHelp === ex.id ? 'help-circle' : 'help-circle-outline'}
                    size={22}
                    color={theme.muscu}
                  />
                </PressableScale>
              ) : null}
              <Pressable onPress={() => removeExercise(ex.id)} hitSlop={10}>
                <MaterialCommunityIcons name="trash-can-outline" size={20} color={theme.textSecondary} />
              </Pressable>
            </View>

            {ex.howTo && openHelp === ex.id ? (
              <View
                style={{
                  backgroundColor: theme.background,
                  borderRadius: Radius.sm,
                  borderLeftWidth: 3,
                  borderLeftColor: theme.muscu,
                  padding: 12,
                }}>
                <Text style={{ color: theme.textSecondary, fontSize: 13, lineHeight: 19 }}>
                  {ex.howTo}
                </Text>
              </View>
            ) : null}

            {ex.sets.map((s, i) => (
              <View
                key={i}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <PressableScale
                  onPress={() => toggleSet(ex.id, i)}
                  haptic="success"
                  scaleTo={0.85}
                  hitSlop={6}
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: 13,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderWidth: s.done ? 0 : 1.5,
                    borderColor: theme.border,
                    backgroundColor: s.done ? theme.muscu : 'transparent',
                  }}>
                  {s.done ? (
                    <MaterialCommunityIcons name="check" size={16} color="#fff" />
                  ) : (
                    <Text
                      style={{
                        color: theme.textSecondary,
                        fontWeight: '700',
                        fontSize: 12,
                        fontVariant: ['tabular-nums'],
                      }}>
                      {i + 1}
                    </Text>
                  )}
                </PressableScale>
                <View
                  style={{ flex: 1, flexDirection: 'row', gap: 10, opacity: s.done ? 0.45 : 1 }}>
                  <Stepper
                    value={s.reps}
                    suffix={ex.repUnit ?? 'reps'}
                    step={1}
                    min={1}
                    onChange={(v) => updateSet(ex.id, i, { reps: v })}
                  />
                  <Stepper
                    value={s.weightKg}
                    suffix="kg"
                    step={2.5}
                    min={0}
                    onChange={(v) => updateSet(ex.id, i, { weightKg: v })}
                  />
                </View>
                <Pressable onPress={() => removeSet(ex.id, i)} hitSlop={8}>
                  <MaterialCommunityIcons name="close-circle-outline" size={20} color={theme.textSecondary} />
                </Pressable>
              </View>
            ))}

            <PressableScale
              onPress={() => addSet(ex.id)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 4 }}>
              <MaterialCommunityIcons name="plus-circle-outline" size={18} color={theme.muscu} />
              <Text style={{ color: theme.muscu, fontWeight: '700' }}>Ajouter une série</Text>
            </PressableScale>
          </Card>
        ))}

        {/* Ajout d'exercice */}
        <Card style={{ gap: 12 }}>
          <Text style={{ ...Type.subtitle, color: theme.text }}>Ajouter un exercice</Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder="Nom de l'exercice"
              placeholderTextColor={theme.textMuted}
              onSubmitEditing={() => addExercise(draft)}
              returnKeyType="done"
              style={{
                flex: 1,
                color: theme.text,
                backgroundColor: theme.background,
                borderRadius: Radius.sm,
                borderWidth: 1,
                borderColor: theme.border,
                paddingHorizontal: 12,
                paddingVertical: 10,
                fontSize: 15,
              }}
            />
            <PressableScale
              onPress={() => addExercise(draft)}
              haptic="light"
              style={{
                backgroundColor: theme.muscu,
                borderRadius: Radius.sm,
                paddingHorizontal: 16,
                justifyContent: 'center',
              }}>
              <MaterialCommunityIcons name="plus" size={22} color="#fff" />
            </PressableScale>
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {COMMON.map((name) => (
              <Chip key={name} label={name} color={theme.muscu} onPress={() => addExercise(name)} />
            ))}
          </View>
        </Card>
      </ScrollView>

      {/* Contrôles */}
      <View
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          paddingHorizontal: 16,
          paddingTop: 12,
          paddingBottom: insets.bottom + 12,
          backgroundColor: theme.backgroundElement,
          borderTopWidth: 1,
          borderTopColor: theme.hairline,
          ...Elevation.lg,
        }}>
        <Button
          title="Terminer la séance"
          icon="flag-checkered"
          color={theme.muscu}
          loading={saving}
          onPress={finish}
        />
      </View>
    </View>
  );
}

function Summary({
  label,
  value,
  theme,
  color,
}: {
  label: string;
  value: string;
  theme: ReturnType<typeof useTheme>;
  color?: string;
}) {
  return (
    <View style={{ alignItems: 'center', gap: 2 }}>
      <Text style={{ ...Type.metric, fontSize: 20, color: color ?? theme.text }}>{value}</Text>
      <Text style={{ ...Type.caption, color: theme.textSecondary }}>{label}</Text>
    </View>
  );
}

function Stepper({
  value,
  suffix,
  step,
  min,
  onChange,
}: {
  value: number;
  suffix: string;
  step: number;
  min: number;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  const fmt = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

  return (
    <View
      style={{
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: theme.background,
        borderRadius: 10,
        borderWidth: 1,
        borderColor: theme.border,
        paddingHorizontal: 4,
        paddingVertical: 4,
      }}>
      <Pressable
        onPress={() => onChange(Math.max(min, value - step))}
        hitSlop={6}
        style={{ padding: 4 }}>
        <MaterialCommunityIcons name="minus" size={18} color={theme.text} />
      </Pressable>
      <View style={{ alignItems: 'center', flex: 1 }}>
        <Text style={{ color: theme.text, fontWeight: '800', fontSize: 15, fontVariant: ['tabular-nums'] }}>
          {fmt(value)}
        </Text>
        <Text style={{ color: theme.textSecondary, fontSize: 10 }}>{suffix}</Text>
      </View>
      <Pressable onPress={() => onChange(value + step)} hitSlop={6} style={{ padding: 4 }}>
        <MaterialCommunityIcons name="plus" size={18} color={theme.text} />
      </Pressable>
    </View>
  );
}
