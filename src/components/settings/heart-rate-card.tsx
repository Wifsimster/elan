import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ActivityIndicator, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { SettingCardHeader } from '@/components/setting-card-header';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : appairage et état de la ceinture cardiaque (BLE). */
export function HeartRateCard() {
  const theme = useTheme();
  const hr = useHeartRate();

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="heart-pulse" color={theme.heart} title="Ceinture cardiaque" />

      <HrStatusLine />

      {hr.status === 'connected' ? (
        <Button
          title="Déconnecter"
          icon="bluetooth-off"
          variant="secondary"
          color={theme.accent}
          onPress={hr.disconnect}
        />
      ) : (
        <Button
          title={
            hr.status === 'scanning'
              ? 'Recherche en cours…'
              : hr.status === 'reconnecting'
                ? 'Reconnexion…'
                : 'Rechercher une ceinture'
          }
          icon="bluetooth"
          color={theme.accent}
          loading={
            hr.status === 'scanning' || hr.status === 'connecting' || hr.status === 'reconnecting'
          }
          onPress={hr.startScan}
        />
      )}

      {hr.error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{hr.error}</Text> : null}

      {hr.status === 'scanning' && hr.scanned.length === 0 ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <ActivityIndicator color={theme.textSecondary} />
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {"Active ta ceinture et porte-la pour qu'elle soit détectée."}
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
  );
}

/** Ligne d'état de la ceinture : pastille colorée + libellé + FC live si connectée. */
function HrStatusLine() {
  const theme = useTheme();
  const { status, bpm, device } = useHeartRate();

  const map: Record<string, { label: string; color: string }> = {
    connected: { label: device ? `Connectée · ${device.name}` : 'Connectée', color: theme.success },
    connecting: { label: 'Connexion…', color: theme.warning },
    reconnecting: { label: 'Reconnexion…', color: theme.warning },
    scanning: { label: 'Recherche…', color: theme.warning },
    error: { label: 'Erreur', color: theme.danger },
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
