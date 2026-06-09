import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ExerciseCatalog } from '@/components/exercise-catalog';
import { getProfile } from '@/lib/db';
import type { CatalogExercise, RecoProfile } from '@/lib/exercises';
import { useTheme } from '@/hooks/use-theme';

const DEFAULT_RECO: RecoProfile = { weightKg: 70, heightCm: 175, sex: null, goal: 'hypertrophie' };

/**
 * Magasin d'exercices accessible hors séance (depuis l'accueil) : parcourir,
 * voir le détail et les charges conseillées, puis démarrer une séance avec
 * l'exercice choisi (transmis à l'écran muscu via le paramètre `add`).
 */
export default function ExercisesScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [profile, setProfile] = useState<RecoProfile>(DEFAULT_RECO);

  useEffect(() => {
    getProfile().then((p) =>
      setProfile({ weightKg: p.weightKg, heightCm: p.heightCm, sex: p.sex, goal: p.goal }),
    );
  }, []);

  const startWith = (ex: CatalogExercise) => {
    router.push({ pathname: '/muscu', params: { add: ex.id } });
  };

  return (
    <View
      style={{
        flex: 1,
        backgroundColor: theme.background,
        paddingTop: 8,
        paddingLeft: insets.left + 16,
        paddingRight: insets.right + 16,
        paddingBottom: insets.bottom,
      }}>
      <ExerciseCatalog profile={profile} onPick={startWith} addLabel="Ajouter à une séance" />
    </View>
  );
}
