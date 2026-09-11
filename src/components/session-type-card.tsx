import { useRef, useState } from 'react';
import { ActivityIndicator, Alert, Text, View } from 'react-native';

import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { Type } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ACTIVITY_META, ACTIVITY_TYPES, isGpsActivity } from '@/lib/activity';
import { changeSessionType } from '@/lib/db';
import { haptics } from '@/lib/haptics';
import { canRetype } from '@/lib/session-retype';
import { retypeSavedSession } from '@/lib/session-finalize';
import type { ActivityType, Session, TrackPoint } from '@/lib/types';

type Props = {
  session: Session;
  /** Points du tracé — servent à réécrire la série FC du miroir Health Connect. */
  points: TrackPoint[];
  /** Appelé après un changement réussi, pour recharger la séance affichée. */
  onChanged: () => void;
};

/**
 * Carte « Type d'activité » du détail de séance : corrige après coup une sortie
 * enregistrée sous le mauvais type. Limitée aux activités tracées au GPS — passer
 * de ou vers la musculation n'aurait pas de sens.
 *
 * Le changement n'est pas cosmétique (calories ré-estimées, cadence effacée), il
 * passe donc par une confirmation qui dit ce qui va bouger.
 */
export function SessionTypeCard({ session, points, onChanged }: Props) {
  const theme = useTheme();
  const [busy, setBusy] = useState(false);
  // Garde de réentrance armée dès la CONFIRMATION : `busy` ne se lève qu'à
  // l'écriture, si bien qu'un double appui ouvrait deux boîtes de dialogue — la
  // seconde échouait ensuite sur une séance déjà convertie, avec une fausse
  // erreur à la clé.
  const pendingRef = useRef(false);

  const apply = async (previousType: ActivityType, to: ActivityType) => {
    setBusy(true);
    try {
      const updated = await changeSessionType(session.id, to);
      if (!updated) {
        haptics.error();
        Alert.alert('Changement impossible', "Le type de cette séance n'a pas pu être modifié.");
        return;
      }
      haptics.success();
      // Miroir santé et sauvegarde : best-effort, ne bloquent pas l'écran.
      retypeSavedSession(previousType, {
        type: updated.type,
        startedAt: updated.startedAt,
        endedAt: updated.endedAt ?? updated.startedAt + updated.durationSec * 1000,
        distanceM: updated.distanceM,
        calories: updated.calories,
        hrSamples: points
          .filter((p) => p.hr != null && p.hr > 0)
          .map((p) => ({ ts: p.ts, hr: p.hr as number })),
      });
      onChanged();
    } catch {
      haptics.error();
      Alert.alert('Changement impossible', 'La séance n’a pas pu être mise à jour. Réessaie.');
    } finally {
      pendingRef.current = false;
      setBusy(false);
    }
  };

  const confirm = (to: ActivityType) => {
    if (pendingRef.current || !canRetype(session.type, to)) return;
    pendingRef.current = true;
    const previousType = session.type;
    const label = ACTIVITY_META[to].label.toLowerCase();
    Alert.alert(
      `Passer en ${label} ?`,
      `Les calories seront recalculées avec le barème « ${label} »${
        previousType === 'velo' ? ', et la cadence du capteur vélo sera effacée' : ''
      }. Le tracé, la durée et la fréquence cardiaque ne changent pas.`,
      [
        {
          text: 'Annuler',
          style: 'cancel',
          onPress: () => {
            pendingRef.current = false;
          },
        },
        { text: 'Changer', onPress: () => apply(previousType, to) },
      ],
      {
        cancelable: true,
        onDismiss: () => {
          pendingRef.current = false;
        },
      },
    );
  };

  return (
    <Card style={{ gap: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <Text style={{ ...Type.overline, color: theme.textSecondary }}>Type d’activité</Text>
        {busy ? <ActivityIndicator color={theme.textSecondary} /> : null}
      </View>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {ACTIVITY_TYPES.filter(isGpsActivity).map((t) => (
          <Chip
            key={t}
            label={ACTIVITY_META[t].shortLabel}
            selected={t === session.type}
            color={theme[ACTIVITY_META[t].colorKey]}
            onPress={() => confirm(t)}
          />
        ))}
      </View>

      <Text style={{ ...Type.caption, color: theme.textSecondary }}>
        Enregistrée sous le mauvais type ? Choisis le bon : les calories sont ré-estimées avec le
        barème de l’activité.
      </Text>
    </Card>
  );
}
