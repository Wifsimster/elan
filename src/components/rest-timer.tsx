import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';

import { PressableScale } from '@/components/pressable-scale';
import { Elevation, Radius, Type } from '@/constants/theme';
import { formatDuration } from '@/lib/format';
import { haptics } from '@/lib/haptics';
import { sounds } from '@/lib/sounds';
import { useTheme } from '@/hooks/use-theme';

type Props = {
  endsAt: number | null;
  onChange: (endsAt: number | null) => void;
  onAdjustPreference?: (deltaSec: number) => void;
};

const LINGER_MS = 5000;

export function RestTimer({ endsAt, onChange, onAdjustPreference }: Props) {
  const theme = useTheme();
  const [now, setNow] = useState(() => Date.now());
  const firedRef = useRef(false);

  useEffect(() => {
    if (endsAt == null) return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [endsAt]);

  useEffect(() => {
    if (endsAt == null) return;
    if (now >= endsAt && !firedRef.current) {
      firedRef.current = true;
      haptics.success();
      sounds.restDone();
    }
    if (now >= endsAt + LINGER_MS) onChange(null);
  }, [now, endsAt, onChange]);

  if (endsAt == null) return null;

  const remainingSec = Math.max(0, Math.ceil((endsAt - now) / 1000));
  const done = remainingSec === 0;

  const adjust = (deltaSec: number) => {
    const base = Math.max(endsAt, now);
    onChange(Math.max(now + 1000, base + deltaSec * 1000));
    onAdjustPreference?.(deltaSec);
  };

  return (
    <View
      style={{
        marginHorizontal: 16,
        marginBottom: 10,
        paddingVertical: 10,
        paddingHorizontal: 14,
        borderRadius: Radius.lg,
        borderCurve: 'continuous',
        backgroundColor: theme.surfaceHigh,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 12,
        ...Elevation.md,
      }}>
      <MaterialCommunityIcons
        name={done ? 'check-circle' : 'timer-sand'}
        size={26}
        color={done ? theme.success : theme.muscu}
      />
      <View style={{ flex: 1 }}>
        <Text style={{ ...Type.overline, color: theme.textSecondary }}>
          {done ? 'Repos terminé' : 'Repos'}
        </Text>
        <Text style={{ ...Type.metric, fontSize: 26, color: done ? theme.success : theme.text }}>
          {formatDuration(remainingSec)}
        </Text>
      </View>

      <PressableScale
        onPress={() => adjust(-15)}
        haptic="selection"
        accessibilityLabel="Réduire le repos de 15 secondes"
        style={pill(theme)}>
        <Text style={{ color: theme.text, fontWeight: '800', fontSize: 12 }}>−15</Text>
      </PressableScale>
      <PressableScale
        onPress={() => adjust(15)}
        haptic="selection"
        accessibilityLabel="Allonger le repos de 15 secondes"
        style={pill(theme)}>
        <Text style={{ color: theme.text, fontWeight: '800', fontSize: 12 }}>+15</Text>
      </PressableScale>
      <PressableScale
        onPress={() => onChange(null)}
        haptic="selection"
        scaleTo={0.85}
        hitSlop={8}
        accessibilityLabel="Fermer le minuteur de repos">
        <MaterialCommunityIcons name="close-circle" size={26} color={theme.textSecondary} />
      </PressableScale>
    </View>
  );
}

const pill = (theme: ReturnType<typeof useTheme>) => ({
  height: 34,
  minWidth: 44,
  alignItems: 'center' as const,
  justifyContent: 'center' as const,
  borderRadius: Radius.pill,
  borderWidth: 1,
  borderColor: theme.border,
  backgroundColor: theme.backgroundElement,
});
