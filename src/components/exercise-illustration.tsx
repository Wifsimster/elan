import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Image, type ImageProps } from 'expo-image';
import { Text, View } from 'react-native';

import { exerciseImages } from '@/components/exercise-images';
import { Gradient } from '@/components/gradient';
import { Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  /** Clé d'illustration photo (paire départ → fin), résolue localement. */
  imageKey?: string;
  /** Glyphe MaterialCommunityIcons de repli si l'exercice n'a pas de photos. */
  icon?: string;
  /** Hauteur des vignettes photo (le repli icône garde sa propre hauteur). */
  height?: number;
};

/**
 * Illustration d'un mouvement de musculation. Quand des photos sont disponibles,
 * affiche les positions de DÉPART puis FINALE côte à côte (flèche centrale pour
 * suggérer le sens du mouvement) ; sinon, retombe sur l'icône posée sur un
 * dégradé. Photos domaine public (free-exercise-db), bundlées dans l'app :
 * aucun accès réseau, conforme à l'approche local-first.
 */
export function ExerciseIllustration({ imageKey, icon, height = 176 }: Props) {
  const theme = useTheme();
  const photos = exerciseImages(imageKey);

  if (!photos) {
    return (
      <Gradient
        colors="muscu"
        style={{
          height: 132,
          borderRadius: Radius.lg,
          alignItems: 'center',
          justifyContent: 'center',
        }}>
        <MaterialCommunityIcons
          name={(icon ?? 'dumbbell') as keyof typeof MaterialCommunityIcons.glyphMap}
          size={72}
          color="#FFFFFF"
        />
      </Gradient>
    );
  }

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <HeroPhoto source={photos[0]} label="Départ" height={height} theme={theme} />
      <MaterialCommunityIcons name="arrow-right" size={22} color={theme.textMuted} />
      <HeroPhoto source={photos[1]} label="Fin" height={height} theme={theme} />
    </View>
  );
}

/** Une vignette de l'illustration : photo cadrée + libellé d'étape en surimpression. */
function HeroPhoto({
  source,
  label,
  height,
  theme,
}: {
  source: ImageProps['source'];
  label: string;
  height: number;
  theme: ReturnType<typeof useTheme>;
}) {
  return (
    <View
      style={{
        flex: 1,
        height,
        borderRadius: Radius.lg,
        overflow: 'hidden',
        backgroundColor: theme.background,
      }}>
      <Image
        source={source}
        contentFit="cover"
        style={{ width: '100%', height: '100%' }}
        accessibilityLabel={`Position « ${label} » du mouvement`}
      />
      <View
        style={{
          position: 'absolute',
          left: 8,
          bottom: 8,
          paddingHorizontal: 10,
          paddingVertical: 4,
          borderRadius: Radius.pill,
          backgroundColor: '#000000B3',
        }}>
        <Text style={{ ...Type.overline, color: '#FFFFFF' }}>{label}</Text>
      </View>
    </View>
  );
}
