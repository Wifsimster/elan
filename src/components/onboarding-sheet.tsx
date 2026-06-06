import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Radius, Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  visible: boolean;
  initialWeightKg: number;
  initialMaxHr: number;
  /** Renvoie le profil saisi ; l'appelant le persiste et marque l'onboarding fait. */
  onDone: (profile: { weightKg: number; maxHr: number }) => void;
};

/**
 * Accueil premier lancement (100 % local). Capture poids + FC max — sans eux,
 * les calories et les zones cardio tournent sur des valeurs par défaut (70 kg /
 * 190 bpm) silencieusement fausses dès la première séance. Rassure aussi sur le
 * fait que rien ne quitte l'appareil. Aucun compte, aucun réseau.
 */
export function OnboardingSheet({ visible, initialWeightKg, initialMaxHr, onDone }: Props) {
  const theme = useTheme();
  const [weightKg, setWeightKg] = useState(initialWeightKg);
  const [maxHr, setMaxHr] = useState(initialMaxHr);

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent>
      <View
        style={{
          flex: 1,
          backgroundColor: '#000000AA',
          justifyContent: 'center',
          padding: 24,
        }}>
        <View
          style={{
            backgroundColor: theme.backgroundElement,
            borderRadius: Radius.xl,
            borderCurve: 'continuous',
            padding: 24,
            gap: 16,
          }}>
          <View
            style={{
              width: 64,
              height: 64,
              borderRadius: Radius.lg,
              borderCurve: 'continuous',
              backgroundColor: theme.accent + '22',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
            <MaterialCommunityIcons name="hand-wave" size={32} color={theme.accent} />
          </View>

          <View style={{ gap: 6 }}>
            <Text style={{ ...Type.title, color: theme.text }}>Bienvenue dans Élan</Text>
            <Text style={{ ...Type.body, color: theme.textSecondary }}>
              Ton carnet d&apos;entraînement vélo & muscu.
            </Text>
          </View>

          <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
            <MaterialCommunityIcons name="lock-outline" size={18} color={theme.success} />
            <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1, lineHeight: 19 }}>
              Tout reste sur cet appareil — aucun compte, aucun serveur, aucune donnée envoyée sur
              internet.
            </Text>
          </View>

          <View style={{ gap: 12, paddingTop: 4 }}>
            <Text style={{ ...Type.label, color: theme.textSecondary }}>
              Renseigne ton profil pour des calories et des zones cardio justes :
            </Text>
            <Stepper label="Poids" value={weightKg} unit="kg" step={1} min={30} max={200} onChange={setWeightKg} />
            <Stepper label="FC max" value={maxHr} unit="bpm" step={1} min={120} max={220} onChange={setMaxHr} />
          </View>

          <Text style={{ ...Type.caption, color: theme.textMuted }}>
            Modifiable à tout moment dans Réglages.
          </Text>

          <Button title="C'est parti" icon="arrow-right" onPress={() => onDone({ weightKg, maxHr })} />
        </View>
      </View>
    </Modal>
  );
}

function Stepper({
  label,
  value,
  unit,
  step,
  min,
  max,
  onChange,
}: {
  label: string;
  value: number;
  unit: string;
  step: number;
  min: number;
  max: number;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <Pressable
          onPress={() => onChange(Math.max(min, value - step))}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel={`Diminuer ${label}`}>
          <MaterialCommunityIcons name="minus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
        <Text
          accessibilityLabel={`${label} : ${value} ${unit}`}
          maxFontSizeMultiplier={1.3}
          style={{
            color: theme.text,
            fontSize: 17,
            fontWeight: '800',
            minWidth: 70,
            textAlign: 'center',
            fontVariant: ['tabular-nums'],
          }}>
          {value} {unit}
        </Text>
        <Pressable
          onPress={() => onChange(Math.min(max, value + step))}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel={`Augmenter ${label}`}>
          <MaterialCommunityIcons name="plus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
      </View>
    </View>
  );
}
