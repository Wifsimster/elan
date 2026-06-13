import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { PressableScale } from '@/components/pressable-scale';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingStepper } from '@/components/settings/setting-stepper';
import { Type } from '@/constants/theme';
import { getProfile, saveProfile } from '@/lib/db';
import { GOALS, goalSpec } from '@/lib/exercises';
import type { Profile, Sex } from '@/lib/types';
import { useTheme } from '@/hooks/use-theme';

/** Options de sexe pour la personnalisation des charges (null = non précisé). */
const SEX_OPTIONS: { value: Sex; label: string }[] = [
  { value: null, label: 'Non précisé' },
  { value: 'h', label: 'Homme' },
  { value: 'f', label: 'Femme' },
];

/**
 * Carte Réglages : profil utilisateur (poids, taille, FC max, objectif, sexe).
 * Pilote calories, zones cardio et reps/charge conseillées. Le poids met aussi
 * à jour le journal via la page dédiée.
 */
export function ProfileCard() {
  const theme = useTheme();
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

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="account-outline" color={theme.accent} title="Profil" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {'Utilisé pour estimer les calories et les zones cardio, et pour conseiller reps et charge dans le catalogue.'}
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

      {/* Journal de poids : historique des pesées + courbe (page dédiée). */}
      <Link href="/poids" asChild>
        <PressableScale
          haptic="light"
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            paddingVertical: 10,
            borderTopWidth: 1,
            borderTopColor: theme.hairline,
            borderBottomWidth: 1,
            borderBottomColor: theme.hairline,
          }}>
          <MaterialCommunityIcons name="scale-bathroom" size={20} color={theme.accent} />
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ color: theme.text, fontWeight: '600', fontSize: 15 }}>
              Journal de poids
            </Text>
            <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
              Note tes pesées et suis leur évolution. La dernière met à jour le profil.
            </Text>
          </View>
          <MaterialCommunityIcons name="chevron-right" size={20} color={theme.textMuted} />
        </PressableScale>
      </Link>

      <SettingStepper
        label="Taille"
        value={profile?.heightCm ?? 175}
        unit="cm"
        step={1}
        min={120}
        max={220}
        onChange={(v) => patchProfile({ heightCm: v })}
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

      {/* Objectif d'entraînement : pilote reps/charge conseillées */}
      <View style={{ gap: 8, paddingTop: 4 }}>
        <Text style={{ ...Type.label, color: theme.textSecondary }}>
          Objectif (ce que tu cherches à faire)
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {GOALS.map((g) => (
            <Chip
              key={g.id}
              label={g.label}
              selected={(profile?.goal ?? 'hypertrophie') === g.id}
              color={theme.muscu}
              onPress={() => patchProfile({ goal: g.id })}
            />
          ))}
        </View>
        <Text style={{ color: theme.textMuted, fontSize: 12 }}>
          {goalSpec(profile?.goal ?? 'hypertrophie').blurb}
        </Text>
      </View>

      {/* Sexe : affine les charges conseillées (optionnel) */}
      <View style={{ gap: 8 }}>
        <Text style={{ ...Type.label, color: theme.textSecondary }}>
          Sexe (affine les charges conseillées)
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {SEX_OPTIONS.map((o) => (
            <Chip
              key={o.label}
              label={o.label}
              selected={(profile?.sex ?? null) === o.value}
              color={theme.accent}
              onPress={() => patchProfile({ sex: o.value })}
            />
          ))}
        </View>
      </View>
    </Card>
  );
}
