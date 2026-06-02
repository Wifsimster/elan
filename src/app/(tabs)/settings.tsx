import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { Type } from '@/constants/theme';
import { clearAllData, getProfile, saveProfile } from '@/lib/db';
import type { Profile } from '@/lib/types';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useTheme } from '@/hooks/use-theme';

export default function SettingsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const hr = useHeartRate();

  const [profile, setProfile] = useState<Profile | null>(null);

  useEffect(() => {
    getProfile().then(setProfile);
  }, []);

  const patchProfile = (patch: Partial<Profile>) => {
    setProfile((prev) => {
      if (!prev) return prev;
      const next = { ...prev, ...patch };
      saveProfile(next);
      return next;
    });
  };

  const confirmClear = () => {
    Alert.alert(
      'Effacer toutes les données ?',
      'Toutes les séances enregistrées seront supprimées définitivement. Le profil et la ceinture appairée sont conservés.',
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Tout effacer', style: 'destructive', onPress: () => clearAllData() },
      ],
    );
  };

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 40,
        paddingHorizontal: 16,
        gap: 16,
      }}>
      <Text style={{ ...Type.title, color: theme.text }}>Réglages</Text>

      {/* Ceinture cardiaque */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="heart-pulse" size={22} color={theme.heart} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
            Ceinture cardiaque
          </Text>
        </View>

        <HrStatusLine />

        {hr.status === 'connected' ? (
          <Button
            title="Déconnecter"
            icon="bluetooth-off"
            variant="secondary"
            color={theme.heart}
            onPress={hr.disconnect}
          />
        ) : (
          <Button
            title={hr.status === 'scanning' ? 'Recherche en cours…' : 'Rechercher une ceinture'}
            icon="bluetooth"
            color={theme.heart}
            loading={hr.status === 'scanning' || hr.status === 'connecting'}
            onPress={hr.startScan}
          />
        )}

        {hr.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{hr.error}</Text>
        ) : null}

        {hr.status === 'scanning' && hr.scanned.length === 0 ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <ActivityIndicator color={theme.textSecondary} />
            <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
              {"Activez votre ceinture et portez-la pour qu'elle soit détectée."}
            </Text>
          </View>
        ) : null}

        {hr.scanned.map((d) => (
          <PressableScale
            key={d.id}
            onPress={() => hr.connect(d.id)}
            haptic="light"
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 10,
              paddingVertical: 10,
              borderTopWidth: 1,
              borderTopColor: theme.hairline,
            }}>
            <MaterialCommunityIcons name="heart-flash" size={20} color={theme.heart} />
            <Text style={{ color: theme.text, flex: 1, fontWeight: '600' }}>{d.name}</Text>
            <MaterialCommunityIcons name="chevron-right" size={20} color={theme.textMuted} />
          </PressableScale>
        ))}

        {hr.status === 'unsupported' ? (
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {"Le Bluetooth n'est disponible que sur l'application Android/iOS (development build)."}
          </Text>
        ) : null}
      </Card>

      {/* Profil */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="account-outline" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Profil</Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          Utilisé pour estimer les calories et les zones cardio.
        </Text>

        <SettingStepper
          label="Poids"
          value={profile?.weightKg ?? 70}
          unit="kg"
          step={1}
          min={30}
          max={200}
          onChange={(v) => patchProfile({ weightKg: v })}
        />
        <SettingStepper
          label="FC max"
          value={profile?.maxHr ?? 190}
          unit="bpm"
          step={1}
          min={120}
          max={220}
          onChange={(v) => patchProfile({ maxHr: v })}
        />
      </Card>

      {/* Données */}
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="database-outline" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Données</Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
          <MaterialCommunityIcons name="lock-outline" size={18} color={theme.success} />
          <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1 }}>
            {"Toutes vos données restent sur cet appareil (base SQLite locale). Rien n'est envoyé sur internet."}
          </Text>
        </View>
        <Button
          title="Effacer toutes les séances"
          icon="trash-can-outline"
          variant="danger"
          onPress={confirmClear}
        />
      </Card>
    </ScrollView>
  );
}

function HrStatusLine() {
  const theme = useTheme();
  const { status, bpm, device } = useHeartRate();

  const map: Record<string, { label: string; color: string }> = {
    connected: { label: device ? `Connectée · ${device.name}` : 'Connectée', color: theme.success },
    connecting: { label: 'Connexion…', color: theme.warning },
    scanning: { label: 'Recherche…', color: theme.warning },
    error: { label: 'Erreur', color: theme.heart },
    idle: { label: 'Non connectée', color: theme.textSecondary },
    unsupported: { label: 'Non disponible', color: theme.textSecondary },
  };
  const s = map[status] ?? map.idle;

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: s.color }} />
        <Text style={{ color: theme.text, fontWeight: '600' }}>{s.label}</Text>
      </View>
      {status === 'connected' ? (
        <Text style={{ color: theme.heart, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
          {bpm != null ? `${bpm} bpm` : '··'}
        </Text>
      ) : null}
    </View>
  );
}

function SettingStepper({
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
        <Pressable onPress={() => onChange(Math.max(min, value - step))} hitSlop={8}>
          <MaterialCommunityIcons name="minus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
        <Text
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
        <Pressable onPress={() => onChange(Math.min(max, value + step))} hitSlop={8}>
          <MaterialCommunityIcons name="plus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
      </View>
    </View>
  );
}
