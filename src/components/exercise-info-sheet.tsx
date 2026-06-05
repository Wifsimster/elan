import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useCallback } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  runOnJS,
  SlideInDown,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Gradient } from '@/components/gradient';
import { Motion, Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** Données d'illustration d'un exercice affichées dans la fiche. */
export type ExerciseInfo = {
  name: string;
  /** Glyphe MaterialCommunityIcons illustrant le mouvement. */
  icon?: string;
  /** Groupes musculaires sollicités, en pastilles. */
  muscles?: string[];
  /** Explication « comment faire ». */
  howTo?: string;
};

type Props = {
  /** Exercice à présenter ; `null` ferme la fiche. */
  exercise: ExerciseInfo | null;
  onClose: () => void;
};

/** Seuil de glissement (px) au-delà duquel le geste ferme la fiche. */
const DISMISS_THRESHOLD = 120;

/**
 * Bottom sheet illustrée détaillant un exercice : héros en dégradé avec une
 * icône du mouvement, muscles ciblés en pastilles et explication. Glisser la
 * poignée vers le bas ou toucher le voile la referme. 100 % local (icône +
 * dégradé SVG), conforme au design system PULSE.
 */
export function ExerciseInfoSheet({ exercise, onClose }: Props) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const translateY = useSharedValue(0);

  const visible = exercise != null;

  // Referme en remettant la fiche en position haute : le shared value persiste
  // entre les montages, on le réinitialise donc pour la prochaine ouverture.
  const handleClose = useCallback(() => {
    // eslint-disable-next-line react-hooks/immutability
    translateY.value = 0;
    onClose();
  }, [onClose, translateY]);

  const pan = Gesture.Pan()
    .onChange((e) => {
      // On ne suit le doigt que vers le bas.
      // eslint-disable-next-line react-hooks/immutability
      translateY.value = Math.max(0, translateY.value + e.changeY);
    })
    .onEnd((e) => {
      if (translateY.value > DISMISS_THRESHOLD || e.velocityY > 800) {
        runOnJS(handleClose)();
      } else {
        // eslint-disable-next-line react-hooks/immutability
        translateY.value = withSpring(0, Motion.spring.snappy);
      }
    });

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={handleClose}>
      <Pressable
        onPress={handleClose}
        style={{ flex: 1, backgroundColor: '#00000099', justifyContent: 'flex-end' }}>
        {exercise ? (
          <Animated.View
            entering={SlideInDown.springify().damping(20).stiffness(220)}
            style={[
              sheetStyle,
              {
                backgroundColor: theme.backgroundElement,
                borderTopLeftRadius: Radius.xl,
                borderTopRightRadius: Radius.xl,
                paddingBottom: insets.bottom + 16,
                maxHeight: '88%',
                overflow: 'hidden',
              },
            ]}>
            {/* Empêche la propagation du tap au voile (sinon fermeture). */}
            <Pressable onPress={(e) => e.stopPropagation()}>
              {/* Poignée de glissement : zone de drag pour refermer. */}
              <GestureDetector gesture={pan}>
                <View style={{ alignItems: 'center', paddingTop: 10, paddingBottom: 4 }}>
                  <View
                    style={{
                      width: 40,
                      height: 5,
                      borderRadius: Radius.pill,
                      backgroundColor: theme.border,
                    }}
                  />
                </View>
              </GestureDetector>

              {/* Héros illustré */}
              <Gradient
                colors="muscu"
                style={{
                  height: 132,
                  margin: 16,
                  borderRadius: Radius.lg,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}>
                <MaterialCommunityIcons
                  name={(exercise.icon ?? 'dumbbell') as keyof typeof MaterialCommunityIcons.glyphMap}
                  size={72}
                  color="#FFFFFF"
                />
              </Gradient>

              <ScrollView
                style={{ maxHeight: 420 }}
                contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 8 }}
                showsVerticalScrollIndicator={false}>
                <Text style={{ ...Type.headline, color: theme.text }}>{exercise.name}</Text>

                {exercise.muscles && exercise.muscles.length > 0 ? (
                  <View style={{ marginTop: 16 }}>
                    <Text style={{ ...Type.overline, color: theme.textMuted, marginBottom: 8 }}>
                      Muscles ciblés
                    </Text>
                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                      {exercise.muscles.map((m) => (
                        <View
                          key={m}
                          style={{
                            paddingHorizontal: 12,
                            paddingVertical: 7,
                            borderRadius: Radius.pill,
                            backgroundColor: theme.muscu + '1F',
                          }}>
                          <Text style={{ color: theme.muscu, fontWeight: '700', fontSize: 13 }}>
                            {m}
                          </Text>
                        </View>
                      ))}
                    </View>
                  </View>
                ) : null}

                {exercise.howTo ? (
                  <View style={{ marginTop: 20 }}>
                    <Text style={{ ...Type.overline, color: theme.textMuted, marginBottom: 8 }}>
                      Exécution
                    </Text>
                    <Text style={{ color: theme.text, fontSize: 15, lineHeight: 23 }}>
                      {exercise.howTo}
                    </Text>
                  </View>
                ) : null}
              </ScrollView>
            </Pressable>
          </Animated.View>
        ) : null}
      </Pressable>
    </Modal>
  );
}
