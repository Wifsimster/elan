import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  AppState,
  BackHandler,
  Modal,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { ExerciseCatalog } from '@/components/exercise-catalog';
import { ExerciseInfoSheet } from '@/components/exercise-info-sheet';
import { PressableScale } from '@/components/pressable-scale';
import { RestTimer } from '@/components/rest-timer';
import { Elevation, Radius, Type } from '@/constants/theme';
import { estimateCalories } from '@/lib/calories';
import {
  getProfile,
  getSetting,
  lastWeightByExercise,
  saveMuscuSession,
  setSetting,
} from '@/lib/db';
import {
  catalogById,
  exerciseHowTo,
  goalSpec,
  recoHint,
  recommend,
  type CatalogExercise,
  type RecoProfile,
} from '@/lib/exercises';
import { formatDuration } from '@/lib/format';
import {
  clearLiveSessionNotification,
  ensureNotificationPermission,
  showLiveSessionNotification,
} from '@/lib/live-notification';
import { clearMuscuDraft, loadMuscuDraft, saveMuscuDraft } from '@/lib/muscu-draft';
import { muscuStats, muscuSummary } from '@/lib/muscu-stats';
import { difficultyLabel } from '@/lib/progression-advice';
import { pushDownsampled, summarizeHr } from '@/lib/samples';
import { finalizeSavedSession } from '@/lib/session-finalize';
import type { Difficulty, HrSample } from '@/lib/types';
import { TEMPLATES, targetHint, templateById, type WorkoutTemplate } from '@/lib/program';
import { targetsForExercises } from '@/lib/auto-progression';
import { nowMs } from '@/lib/time';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useScreenContentStyle } from '@/hooks/use-screen-layout';
import { useStopwatch } from '@/hooks/use-stopwatch';
import { useTheme } from '@/hooks/use-theme';

type SetRow = { reps: number; weightKg: number; done?: boolean };
type Exercise = {
  id: string;
  name: string;
  sets: SetRow[];
  target?: string;
  repUnit?: string;
  lastWeight?: number;
  howTo?: string;
  muscles?: string[];
  icon?: string;
  imageKey?: string;
  difficulty?: Difficulty;
  bump?: number;
  bumpKind?: 'load' | 'time';
};

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

function bumpHint(bump: number, kind?: 'load' | 'time'): string {
  const unit = kind === 'time' ? 's' : 'kg';
  const amount = kind === 'time' ? String(Math.abs(bump)) : fmtKg(Math.abs(bump));
  return bump > 0
    ? `Auto : +${amount} ${unit} cette semaine`
    : `Allégé : −${amount} ${unit} cette semaine`;
}

function buildCatalogExercise(
  ex: CatalogExercise,
  profile: RecoProfile,
  lastWeight?: number,
): Exercise {
  const rec = recommend(profile, ex);
  const reps = Math.round((rec.repsMin + rec.repsMax) / 2);
  const unloaded = ex.timed || ex.loadFactor === 0;
  const weight = unloaded ? 0 : (lastWeight ?? rec.weightKg);
  return {
    id: nextId(),
    name: ex.name,
    target: recoHint(ex, rec),
    repUnit: rec.timed ? 'sec' : 'reps',
    howTo: exerciseHowTo(ex.id),
    muscles: ex.muscles,
    icon: ex.icon,
    imageKey: ex.imageKey,
    lastWeight: unloaded ? undefined : lastWeight,
    sets: Array.from({ length: rec.sets }, () => ({ reps, weightKg: weight })),
  };
}

export default function MuscuScreen() {
  useKeepAwake();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const contentStyle = useScreenContentStyle();
  const router = useRouter();

  const watch = useStopwatch();
  const { bpm, subscribe: subscribeHr } = useHeartRate();
  const { template, add } = useLocalSearchParams<{ template?: string; add?: string }>();

  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [paused, setPaused] = useState(false);
  const [infoExercise, setInfoExercise] = useState<Exercise | null>(null);
  const [catalogOpen, setCatalogOpen] = useState(false);
  const [recoProfile, setRecoProfile] = useState<RecoProfile>({
    weightKg: 70,
    heightCm: 175,
    sex: null,
    goal: 'hypertrophie',
  });
  const [restEndsAt, setRestEndsAt] = useState<number | null>(null);

  const startedAtRef = useRef<number>(0);
  const muscuIdRef = useRef<number | null>(null);
  const savingRef = useRef<boolean>(false);
  const hrSamplesRef = useRef<HrSample[]>([]);
  const pausedRef = useRef<boolean>(false);
  const weightRef = useRef<number>(70);
  const maxHrRef = useRef<number>(190);
  const restDurationRef = useRef<number>(90);
  const hydratedRef = useRef<boolean>(false);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  const loadTemplate = async (t: WorkoutTemplate) => {
    const targets = await targetsForExercises(t.exercises);
    setExercises(
      t.exercises.map((ex) => {
        const tgt = targets[ex.name];
        return {
          id: nextId(),
          name: ex.name,
          target: targetHint(ex),
          repUnit: ex.timed ? 'sec' : 'reps',
          howTo: ex.howTo,
          muscles: ex.muscles,
          icon: ex.icon,
          imageKey: ex.imageKey,
          lastWeight: tgt.lastWeightKg,
          bump: tgt.bump,
          bumpKind: tgt.bumpKind,
          sets: Array.from({ length: ex.sets }, () => ({
            reps: tgt.reps,
            weightKg: tgt.weightKg,
          })),
        };
      }),
    );
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const p = await getProfile();
      if (cancelled) return;
      weightRef.current = p.weightKg;
      maxHrRef.current = p.maxHr;
      const rp: RecoProfile = { weightKg: p.weightKg, heightCm: p.heightCm, sex: p.sex, goal: p.goal };
      setRecoProfile(rp);

      const savedRest = Number(await getSetting('rest_seconds'));
      if (cancelled) return;
      if (Number.isFinite(savedRest) && savedRest > 0) {
        restDurationRef.current = Math.max(15, Math.min(600, savedRest));
      } else {
        restDurationRef.current = goalSpec(p.goal).restSec;
      }

      const draft = await loadMuscuDraft();
      if (cancelled) return;

      if (draft) {
        startedAtRef.current = draft.startedAt;
        hrSamplesRef.current = draft.hrSamples;
        const restored = (draft.exercises as Exercise[]).map((e) => ({ ...e, id: nextId() }));
        setExercises(restored);
        watch.seed(draft.elapsedSec);
        setPaused(true);
      } else {
        startedAtRef.current = nowMs();
        watch.start();
        ensureNotificationPermission();
        showLiveSessionNotification('muscu');
        const preset = templateById(template);
        if (preset) await loadTemplate(preset);
      }

      const addId = typeof add === 'string' ? add : undefined;
      if (addId) {
        const cat = catalogById(addId);
        if (cat) {
          const last = await lastWeightByExercise([cat.name]);
          if (!cancelled) {
            setExercises((prev) => [...prev, buildCatalogExercise(cat, rp, last[cat.name])]);
          }
        }
      }
      hydratedRef.current = true;
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const addCatalogExercise = async (ex: CatalogExercise) => {
    const last = await lastWeightByExercise([ex.name]);
    setExercises((prev) => [...prev, buildCatalogExercise(ex, recoProfile, last[ex.name])]);
  };

  useEffect(() => {
    return subscribeHr((sample) => {
      if (pausedRef.current) return;
      pushDownsampled(hrSamplesRef.current, sample, (s) => s.hr);
    });
  }, [subscribeHr]);

  const pause = () => {
    watch.pause();
    setPaused(true);
  };

  const resume = () => {
    watch.start();
    setPaused(false);
    showLiveSessionNotification('muscu');
  };

  const addExercise = (name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setExercises((prev) => [
      ...prev,
      { id: nextId(), name: trimmed, sets: [{ reps: 10, weightKg: 20 }] },
    ]);
    setDraft('');
  };

  const removeExercise = (id: string) => {
    const exercise = exercises.find((e) => e.id === id);
    Alert.alert(
      'Supprimer cet exercice ?',
      exercise ? `« ${exercise.name} » sera retiré de la séance.` : 'Cet exercice sera retiré de la séance.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Supprimer',
          style: 'destructive',
          onPress: () => setExercises((prev) => prev.filter((e) => e.id !== id)),
        },
      ],
    );
  };

  const addSet = (id: string) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id
          ? { ...e, sets: [...e.sets, { ...(e.sets[e.sets.length - 1] ?? { reps: 10, weightKg: 20 }) }] }
          : e,
      ),
    );

  const dropSet = (id: string, idx: number) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id ? { ...e, sets: e.sets.filter((_, i) => i !== idx) } : e,
      ),
    );

  const removeSet = (id: string, idx: number) => {
    const done = exercises.find((e) => e.id === id)?.sets[idx]?.done ?? false;
    if (!done) {
      dropSet(id, idx);
      return;
    }
    Alert.alert('Supprimer cette série ?', 'Cette série effectuée sera retirée.', [
      { text: 'Annuler', style: 'cancel' },
      { text: 'Supprimer', style: 'destructive', onPress: () => dropSet(id, idx) },
    ]);
  };

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

  const toggleSet = (id: string, idx: number) => {
    const wasDone = exercises.find((e) => e.id === id)?.sets[idx]?.done ?? false;
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id
          ? { ...e, sets: e.sets.map((s, i) => (i === idx ? { ...s, done: !s.done } : s)) }
          : e,
      ),
    );
    if (!wasDone) setRestEndsAt(nowMs() + restDurationRef.current * 1000);
  };

  const setDifficulty = (id: string, value: Difficulty) =>
    setExercises((prev) =>
      prev.map((e) =>
        e.id === id ? { ...e, difficulty: e.difficulty === value ? undefined : value } : e,
      ),
    );

  const handleRestChange = (next: number | null) => {
    setRestEndsAt(next);
  };

  const adjustRestPreference = (deltaSec: number) => {
    const secs = Math.max(15, Math.min(600, restDurationRef.current + deltaSec));
    restDurationRef.current = secs;
    setSetting('rest_seconds', String(secs));
  };

  const stats = muscuStats(exercises);
  const { totalSets, doneSets, totalVolume } = stats;

  const persistDraft = () => {
    if (totalSets === 0) return;
    saveMuscuDraft({
      version: 1,
      startedAt: startedAtRef.current,
      elapsedSec: watch.getElapsedSec(),
      exercises,
      hrSamples: hrSamplesRef.current,
    });
  };

  useEffect(() => {
    if (!hydratedRef.current) return;
    if (totalSets === 0) {
      clearMuscuDraft();
      return;
    }
    persistDraft();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exercises, paused]);

  const finish = () => {
    if (savingRef.current) return;
    if (totalSets === 0) {
      Alert.alert('Séance vide', 'Ajoute au moins un exercice avant de terminer.');
      return;
    }
    Alert.alert('Terminer la séance ?', 'La séance sera enregistrée.', [
      { text: 'Continuer', style: 'cancel' },
      { text: 'Terminer', onPress: save },
    ]);
  };

  const save = async () => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    watch.pause();
    const durationSec = watch.getElapsedSec();
    const { avgHr, maxHr } = summarizeHr(hrSamplesRef.current);
    const calories = estimateCalories({
      type: 'muscu',
      weightKg: weightRef.current,
      durationSec,
      avgHr,
      maxHr: maxHrRef.current,
    });

    try {
      const endedAt = nowMs();
      const flat = exercises.flatMap((e) =>
        e.sets.map((s, i) => ({
          exercise: e.name,
          setIndex: i + 1,
          reps: s.reps,
          weightKg: s.weightKg,
          difficulty: e.difficulty ?? null,
        })),
      );
      const id = await saveMuscuSession(
        muscuIdRef.current,
        startedAtRef.current,
        { endedAt, durationSec, avgHr, maxHr, calories, notes: muscuSummary(stats) },
        flat,
      );
      muscuIdRef.current = id;

      await clearMuscuDraft();
      finalizeSavedSession({
        type: 'muscu',
        startedAt: startedAtRef.current,
        endedAt,
        calories,
        hrSamples: hrSamplesRef.current,
      });
      clearLiveSessionNotification();
      router.replace({ pathname: '/session/[id]', params: { id } });
    } catch {
      savingRef.current = false;
      setSaving(false);
      Alert.alert(
        "Échec de l'enregistrement",
        "La séance n'a pas pu être enregistrée. Réessaie.",
      );
    }
  };

  const exitSession = (): boolean => {
    if (saving) return true;
    if (totalSets === 0) {
      clearMuscuDraft();
      clearLiveSessionNotification();
      router.back();
      return true;
    }
    Alert.alert(
      'Quitter la séance ?',
      'Mets-la en pause pour la reprendre plus tard, ou abandonne-la définitivement.',
      [
        { text: 'Continuer', style: 'cancel' },
        {
          text: 'Mettre en pause',
          onPress: async () => {
            watch.pause();
            await persistDraft();
            router.back();
          },
        },
        {
          text: 'Abandonner',
          style: 'destructive',
          onPress: async () => {
            await clearMuscuDraft();
            clearLiveSessionNotification();
            router.back();
          },
        },
      ],
    );
    return true;
  };

  const persistDraftRef = useRef(persistDraft);
  useEffect(() => {
    persistDraftRef.current = persistDraft;
  });
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state !== 'active' && hydratedRef.current && !savingRef.current) {
        persistDraftRef.current();
      }
    });
    return () => sub.remove();
  }, []);

  const exitRef = useRef(exitSession);
  useEffect(() => {
    exitRef.current = exitSession;
  });
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => exitRef.current());
    return () => sub.remove();
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: theme.background }}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          ...contentStyle,
          paddingTop: insets.top + 8,
          paddingBottom: insets.bottom + 120,
          gap: 14,
        }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <PressableScale
            onPress={exitSession}
            haptic="selection"
            scaleTo={0.88}
            hitSlop={12}
            accessibilityLabel="Quitter la séance">
            <MaterialCommunityIcons name="close" size={26} color={theme.text} />
          </PressableScale>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <MaterialCommunityIcons name="dumbbell" size={22} color={theme.muscu} />
            <Text style={{ ...Type.headline, color: theme.text }}>Musculation</Text>
          </View>
          <View style={{ width: 26 }} />
        </View>

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

        {exercises.length === 0 ? (
          <Card style={{ gap: 10 }}>
            <Text style={{ ...Type.subtitle, color: theme.text }}>Charger un programme</Text>
            <Text style={{ ...Type.caption, color: theme.textSecondary, marginTop: -4 }}>
              Choisis un programme pour pré-remplir la séance, ou ajoute tes exercices plus bas.
            </Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {TEMPLATES.map((t) => (
                <PressableScale
                  key={t.id}
                  onPress={() => loadTemplate(t)}
                  haptic="light"
                  style={{
                    flexGrow: 1,
                    flexBasis: '47%',
                    alignItems: 'center',
                    gap: 2,
                    paddingVertical: 12,
                    paddingHorizontal: 8,
                    borderRadius: Radius.sm,
                    borderWidth: 1,
                    borderColor: theme.muscu,
                    backgroundColor: theme.backgroundElement,
                  }}>
                  <Text
                    numberOfLines={1}
                    style={{ color: theme.muscu, fontWeight: '800', fontSize: 15 }}>
                    {t.name}
                  </Text>
                  <Text style={{ color: theme.textSecondary, fontSize: 11 }}>{t.day}</Text>
                </PressableScale>
              ))}
            </View>
          </Card>
        ) : null}

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
                {ex.bump ? (
                  <Text style={{ color: theme.muscu, fontSize: 12, marginTop: 2, fontWeight: '700' }}>
                    {bumpHint(ex.bump, ex.bumpKind)}
                  </Text>
                ) : null}
              </View>
              {ex.howTo ? (
                <PressableScale
                  onPress={() => setInfoExercise(ex)}
                  haptic="selection"
                  hitSlop={8}
                  accessibilityLabel={`Comment faire : ${ex.name}`}>
                  <MaterialCommunityIcons
                    name="information-outline"
                    size={22}
                    color={theme.muscu}
                  />
                </PressableScale>
              ) : null}
              <PressableScale
                onPress={() => removeExercise(ex.id)}
                haptic="selection"
                hitSlop={12}
                accessibilityLabel={`Supprimer l'exercice ${ex.name}`}>
                <MaterialCommunityIcons name="trash-can-outline" size={20} color={theme.textSecondary} />
              </PressableScale>
            </View>

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
                    decimal
                    onChange={(v) => updateSet(ex.id, i, { weightKg: v })}
                  />
                </View>
                <PressableScale
                  onPress={() => removeSet(ex.id, i)}
                  haptic="selection"
                  hitSlop={12}
                  accessibilityLabel={`Supprimer la série ${i + 1}`}>
                  <MaterialCommunityIcons name="close-circle-outline" size={20} color={theme.textSecondary} />
                </PressableScale>
              </View>
            ))}

            <PressableScale
              onPress={() => addSet(ex.id)}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 4 }}>
              <MaterialCommunityIcons name="plus-circle-outline" size={18} color={theme.muscu} />
              <Text style={{ color: theme.muscu, fontWeight: '700' }}>Ajouter une série</Text>
            </PressableScale>

            <View style={{ gap: 6 }}>
              <Text style={{ ...Type.label, color: theme.textSecondary }}>Ressenti</Text>
              <View style={{ flexDirection: 'row', gap: 8 }}>
                {(['facile', 'moyen', 'dur'] as const).map((d) => (
                  <Chip
                    key={d}
                    label={difficultyLabel(d)}
                    selected={ex.difficulty === d}
                    color={theme.muscu}
                    onPress={() => setDifficulty(ex.id, d)}
                  />
                ))}
              </View>
            </View>
          </Card>
        ))}

        <Card style={{ gap: 12 }}>
          <Text style={{ ...Type.subtitle, color: theme.text }}>Ajouter un exercice</Text>
          <Button
            title="Parcourir le catalogue"
            icon="view-grid-outline"
            color={theme.muscu}
            onPress={() => setCatalogOpen(true)}
          />
          <Text style={{ ...Type.caption, color: theme.textSecondary, marginTop: -2 }}>
            Plus de 40 exercices, avec reps et charge conseillés selon ton profil et ton objectif.
          </Text>
          <Text style={{ ...Type.caption, color: theme.textMuted }}>Ou saisis le tien :</Text>
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
              accessibilityLabel="Ajouter l'exercice"
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

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <RestTimer
          key={restEndsAt ?? 'idle'}
          endsAt={restEndsAt}
          onChange={handleRestChange}
          onAdjustPreference={adjustRestPreference}
        />
        <View
          style={{
            paddingLeft: insets.left + 16,
            paddingRight: insets.right + 16,
            paddingTop: 12,
            paddingBottom: insets.bottom + 12,
            backgroundColor: theme.backgroundElement,
            borderTopWidth: 1,
            borderTopColor: theme.hairline,
            flexDirection: 'row',
            gap: 12,
            ...Elevation.lg,
          }}>
          <View style={{ flex: 1 }}>
            <Button
              title={paused ? 'Reprendre' : 'Pause'}
              icon={paused ? 'play' : 'pause'}
              variant="secondary"
              color={theme.muscu}
              onPress={paused ? resume : pause}
            />
          </View>
          <View style={{ flex: 1.4 }}>
            <Button
              title="Terminer"
              icon="flag-checkered"
              color={theme.muscu}
              loading={saving}
              onPress={finish}
            />
          </View>
        </View>
      </View>

      <ExerciseInfoSheet exercise={infoExercise} onClose={() => setInfoExercise(null)} />

      <Modal
        visible={catalogOpen}
        animationType="slide"
        onRequestClose={() => setCatalogOpen(false)}>
        <View
          style={{
            flex: 1,
            backgroundColor: theme.background,
            paddingTop: insets.top + 8,
            paddingLeft: insets.left + 16,
            paddingRight: insets.right + 16,
            paddingBottom: insets.bottom,
          }}>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingBottom: 12,
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <MaterialCommunityIcons name="view-grid-outline" size={22} color={theme.muscu} />
              <Text style={{ ...Type.headline, color: theme.text }}>Catalogue</Text>
            </View>
            <PressableScale
              onPress={() => setCatalogOpen(false)}
              haptic="selection"
              scaleTo={0.88}
              hitSlop={12}
              accessibilityLabel="Fermer le catalogue">
              <MaterialCommunityIcons name="close" size={26} color={theme.text} />
            </PressableScale>
          </View>
          <ExerciseCatalog
            profile={recoProfile}
            addedNames={exercises.map((e) => e.name)}
            onPick={addCatalogExercise}
          />
        </View>
      </Modal>
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
      <Text
        maxFontSizeMultiplier={1.2}
        numberOfLines={1}
        style={{ ...Type.metric, fontSize: 20, color: color ?? theme.text }}>
        {value}
      </Text>
      <Text style={{ ...Type.caption, color: theme.textSecondary }}>{label}</Text>
    </View>
  );
}

function Stepper({
  value,
  suffix,
  step,
  min,
  decimal,
  onChange,
}: {
  value: number;
  suffix: string;
  step: number;
  min: number;
  decimal?: boolean;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  const fmt = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');

  const commit = () => {
    setEditing(false);
    const parsed = parseFloat(text.replace(',', '.'));
    if (!Number.isFinite(parsed)) return;
    const rounded = decimal ? Math.round(parsed * 10) / 10 : Math.round(parsed);
    onChange(Math.max(min, rounded));
  };

  return (
    <View
      style={{
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: theme.background,
        borderRadius: Radius.sm,
        borderCurve: 'continuous',
        borderWidth: 1,
        borderColor: theme.border,
        paddingHorizontal: 4,
        paddingVertical: 4,
      }}>
      <PressableScale
        onPress={() => onChange(Math.max(min, value - step))}
        haptic="selection"
        hitSlop={10}
        accessibilityLabel={`Diminuer ${suffix}`}
        style={{ padding: 4 }}>
        <MaterialCommunityIcons name="minus" size={18} color={theme.text} />
      </PressableScale>
      <PressableScale
        onPress={() => {
          setText(fmt(value).replace(',', '.'));
          setEditing(true);
        }}
        haptic="selection"
        accessibilityLabel={`${fmt(value)} ${suffix}, modifier`}
        style={{ alignItems: 'center', flex: 1 }}>
        {editing ? (
          <TextInput
            value={text}
            onChangeText={setText}
            onBlur={commit}
            onSubmitEditing={commit}
            keyboardType={decimal ? 'decimal-pad' : 'number-pad'}
            returnKeyType="done"
            autoFocus
            selectTextOnFocus
            style={{
              color: theme.text,
              fontWeight: '800',
              fontSize: 15,
              fontVariant: ['tabular-nums'],
              textAlign: 'center',
              minWidth: 48,
              padding: 0,
            }}
          />
        ) : (
          <Text style={{ color: theme.text, fontWeight: '800', fontSize: 15, fontVariant: ['tabular-nums'] }}>
            {fmt(value)}
          </Text>
        )}
        <Text style={{ color: theme.textSecondary, fontSize: 10 }}>{suffix}</Text>
      </PressableScale>
      <PressableScale
        onPress={() => onChange(value + step)}
        haptic="selection"
        hitSlop={10}
        accessibilityLabel={`Augmenter ${suffix}`}
        style={{ padding: 4 }}>
        <MaterialCommunityIcons name="plus" size={18} color={theme.text} />
      </PressableScale>
    </View>
  );
}
