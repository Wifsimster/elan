import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useKeepAwake } from 'expo-keep-awake';
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  BackHandler,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { ExerciseInfoSheet } from '@/components/exercise-info-sheet';
import { PressableScale } from '@/components/pressable-scale';
import { RestTimer } from '@/components/rest-timer';
import { Elevation, Radius, Type } from '@/constants/theme';
import { autoBackup } from '@/lib/backup';
import { estimateCalories } from '@/lib/calories';
import {
  createSession,
  getProfile,
  getSetting,
  lastWeightByExercise,
  replaceMuscuSets,
  setSetting,
  updateSession,
} from '@/lib/db';
import { formatDuration } from '@/lib/format';
import { clearMuscuDraft, loadMuscuDraft, saveMuscuDraft } from '@/lib/muscu-draft';
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
  /** Explication « comment faire », montrée dans la fiche détaillée. */
  howTo?: string;
  /** Groupes musculaires sollicités, affichés dans la fiche. */
  muscles?: string[];
  /** Glyphe MaterialCommunityIcons illustrant le mouvement. */
  icon?: string;
  /** Clé d'illustration photo (paire départ → fin), affichée dans la fiche. */
  imageKey?: string;
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
  const [paused, setPaused] = useState(false);
  const [infoExercise, setInfoExercise] = useState<Exercise | null>(null);
  // Repos inter-séries : horodatage de fin (null = aucun repos en cours).
  const [restEndsAt, setRestEndsAt] = useState<number | null>(null);

  const startedAtRef = useRef<number>(0);
  const hrSamplesRef = useRef<HrSample[]>([]);
  const pausedRef = useRef<boolean>(false);
  const weightRef = useRef<number>(70);
  const maxHrRef = useRef<number>(190);
  // Durée de repos préférée (s), réutilisée à chaque série cochée et persistée.
  const restDurationRef = useRef<number>(90);
  // Passe à true une fois le montage (reprise ou démarrage) terminé : évite
  // d'écraser un brouillon valide pendant la phase d'hydratation.
  const hydratedRef = useRef<boolean>(false);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  // Pré-remplit la séance depuis un programme. Appelé uniquement sur une séance
  // vide (bouton « Charger un programme » ou « Séance du jour ») : on remplace la
  // liste plutôt que d'empiler, pour ne jamais dupliquer les exercices.
  const loadTemplate = async (t: WorkoutTemplate) => {
    const last = await lastWeightByExercise(t.exercises.map((e) => e.name));
    setExercises(
      t.exercises.map((ex) => {
        const lw = last[ex.name];
        return {
          id: nextId(),
          name: ex.name,
          target: targetHint(ex),
          repUnit: ex.timed ? 'sec' : 'reps',
          howTo: ex.howTo,
          muscles: ex.muscles,
          icon: ex.icon,
          imageKey: ex.imageKey,
          // Le gainage n'a pas de charge : on n'affiche pas de « dernière fois ».
          lastWeight: ex.timed ? undefined : lw,
          sets: Array.from({ length: ex.sets }, () => ({
            reps: defaultReps(ex),
            weightKg: ex.timed ? ex.startWeightKg : (lw ?? ex.startWeightKg),
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

      // Durée de repos préférée, mémorisée d'une séance à l'autre.
      const savedRest = Number(await getSetting('rest_seconds'));
      if (cancelled) return;
      if (Number.isFinite(savedRest) && savedRest > 0) {
        restDurationRef.current = Math.max(15, Math.min(600, savedRest));
      }

      const draft = await loadMuscuDraft();
      if (cancelled) return;

      if (draft) {
        // Reprise d'une séance mise en pause : on restaure tout son état et on
        // la laisse en pause (l'utilisateur appuie sur « Reprendre » pour repartir).
        startedAtRef.current = draft.startedAt;
        hrSamplesRef.current = draft.hrSamples;
        // Ré-attribue des ids frais : le compteur `uid` est repassé à 0 au
        // redémarrage de l'app et collisionnerait avec les ids sauvegardés.
        const restored = (draft.exercises as Exercise[]).map((e) => ({ ...e, id: nextId() }));
        setExercises(restored);
        watch.seed(draft.elapsedSec);
        setPaused(true);
      } else {
        // Démarrage normal d'une nouvelle séance.
        startedAtRef.current = nowMs();
        watch.start();
        const preset = templateById(template);
        if (preset) loadTemplate(preset); // pré-chargement depuis la « Séance du jour »
      }
      hydratedRef.current = true;
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Échantillonnage FC : on s'abonne aux trames BLE brutes pour ne pas perdre
  // les paliers (React déduplique les setStates identiques côté `bpm`).
  // Les trames reçues en pause sont ignorées pour ne pas fausser la moyenne.
  useEffect(() => {
    return subscribeHr(({ ts, hr }) => {
      if (pausedRef.current) return;
      // Down-sampling sur palier : un seul échantillon par seconde tant que la FC
      // ne bouge pas. Borne le buffer (et le brouillon re-sérialisé à chaque set)
      // sans altérer moyenne, max, ni l'attachement temporel.
      const buf = hrSamplesRef.current;
      const last = buf[buf.length - 1];
      if (last && last.hr === hr && ts - last.ts < 1000) return;
      buf.push({ ts, hr });
    });
  }, [subscribeHr]);

  const pause = () => {
    watch.pause();
    setPaused(true);
  };

  const resume = () => {
    watch.start();
    setPaused(false);
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

  // Une série déjà cochée (effectuée) ne se supprime pas d'un geste : un pouce
  // qui dérape juste à côté du « + kg » effacerait un résultat sans retour. On
  // confirme dans ce cas ; une série vide non cochée part directement.
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

  // Cocher / décocher une série une fois exécutée (suivi en cours de séance).
  // Cocher une série démarre automatiquement le minuteur de repos.
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

  // Ajuste (±15 s) ou ferme le minuteur de repos. Un ajustement mémorise la
  // nouvelle durée comme préférence (réutilisée à la prochaine série).
  const handleRestChange = (next: number | null) => {
    setRestEndsAt(next);
    if (next != null) {
      const secs = Math.max(15, Math.min(600, Math.round((next - nowMs()) / 1000)));
      restDurationRef.current = secs;
      setSetting('rest_seconds', String(secs)); // best-effort, local
    }
  };

  const totalSets = exercises.reduce((a, e) => a + e.sets.length, 0);
  const doneSets = exercises.reduce((a, e) => a + e.sets.filter((s) => s.done).length, 0);
  const totalVolume = exercises.reduce(
    (a, e) => a + e.sets.reduce((b, s) => b + s.reps * s.weightKg, 0),
    0,
  );

  // Sauvegarde du brouillon (séance en pause, reprenable). Best-effort, local.
  const persistDraft = () =>
    saveMuscuDraft({
      version: 1,
      startedAt: startedAtRef.current,
      elapsedSec: watch.elapsedSec,
      exercises,
      hrSamples: hrSamplesRef.current,
    });

  // Écriture continue : à chaque changement structurel (exercices, pause), on
  // re-sauvegarde, pour qu'une fermeture brutale de l'app ne perde rien.
  useEffect(() => {
    if (!hydratedRef.current || totalSets === 0) return;
    persistDraft();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exercises, paused]);

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

    try {
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

      // Le brouillon n'est effacé qu'APRÈS l'écriture réussie : si une étape
      // ci-dessus lève, la séance reste reprenable au prochain lancement.
      await clearMuscuDraft();
      autoBackup(); // sauvegarde homelab best-effort (ne bloque pas la navigation)
      router.replace({ pathname: '/session/[id]', params: { id } });
    } catch {
      // Échec d'écriture : on ne reste pas bloqué sur « saving ». Le brouillon est
      // intact, l'utilisateur peut réessayer de terminer.
      setSaving(false);
      Alert.alert(
        "Échec de l'enregistrement",
        "La séance n'a pas pu être enregistrée. Réessayez.",
      );
    }
  };

  // Quitter sans terminer : met la séance en pause (brouillon sauvegardé,
  // reprenable plus tard) ou l'abandonne. Une séance vide sort sans rien garder.
  const exitSession = (): boolean => {
    if (saving) return true;
    if (totalSets === 0) {
      clearMuscuDraft();
      router.back();
      return true;
    }
    Alert.alert(
      'Quitter la séance ?',
      'Mettez-la en pause pour la reprendre plus tard, ou abandonnez-la définitivement.',
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
            router.back();
          },
        },
      ],
    );
    return true;
  };

  // Le bouton retour matériel (Android) doit suivre le même chemin que la croix,
  // sinon il quitterait la modale en perdant la séance en mémoire.
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
          paddingTop: insets.top + 8,
          paddingBottom: insets.bottom + 120,
          paddingHorizontal: 16,
          gap: 14,
        }}>
        {/* En-tête */}
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

        {/* Charger un programme — uniquement pour démarrer une séance vide.
            Une fois des exercices en place, le sélecteur disparaît : on ne
            « recharge » pas un programme par-dessus (ça empilait des doublons). */}
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

      {/* Repos inter-séries + contrôles, ancrés en bas */}
      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <RestTimer key={restEndsAt ?? 'idle'} endsAt={restEndsAt} onChange={handleRestChange} />
        <View
          style={{
            paddingHorizontal: 16,
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
          {/* Terminer reçoit plus de poids que Pause (cf. velo.tsx). */}
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
  /** Autorise la saisie décimale (poids : réglage au kg, voire 0,5 kg près). */
  decimal?: boolean;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  const fmt = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(1).replace('.', ','));

  // Saisie directe : taper la valeur ouvre un champ pour la régler à l'unité
  // exacte (les boutons +/- restent pour un ajustement rapide par paliers).
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');

  const commit = () => {
    setEditing(false);
    const parsed = parseFloat(text.replace(',', '.'));
    if (!Number.isFinite(parsed)) return; // saisie vide / invalide : on garde la valeur
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
