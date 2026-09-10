import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { HrZoneColors, Radius, Type } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/hooks/use-theme';
import { formatDuration, formatDurationShort } from '@/lib/format';
import { dominantZone, type ZoneDistribution, type ZoneSlice } from '@/lib/hr-zones';

/** Fourchette de bpm d'une zone, en texte court (« < 114 », « 133–151 », « 171+ »). */
function rangeLabel(slice: ZoneSlice): string {
  if (slice.maxBpm == null) return `${slice.minBpm}+`;
  if (slice.minBpm <= 0) return `< ${slice.maxBpm + 1}`;
  return `${slice.minBpm}–${slice.maxBpm}`;
}

/**
 * Temps passé dans chaque zone cardiaque : barre empilée + détail par zone.
 *
 * Les zones étant ordonnées, la barre suit une rampe d'une seule teinte
 * (`HrZoneColors`) et non cinq couleurs catégorielles ; deux pixels de fond
 * séparent les segments, et chaque zone est nommée en toutes lettres pour que
 * l'information ne repose jamais sur la couleur seule.
 */
export function HrZonesCard({ distribution }: { distribution: ZoneDistribution }) {
  const theme = useTheme();
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const ramp = HrZoneColors[scheme];

  const dominant = dominantZone(distribution);
  const filled = distribution.slices.filter((s) => s.ratio > 0);

  return (
    <Card style={{ gap: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <Text style={{ ...Type.headline, color: theme.text }}>Zones cardiaques</Text>
        <Text style={{ ...Type.caption, color: theme.textMuted }}>
          {formatDurationShort(distribution.totalSec)}
        </Text>
      </View>

      <View
        accessible
        accessibilityLabel={filled
          .map((s) => `${s.label} ${Math.round(s.ratio * 100)} %`)
          .join(', ')}
        style={{ flexDirection: 'row', gap: 2, height: 14 }}>
        {filled.map((slice) => (
          <View
            key={slice.zone}
            style={{
              // × 100 : la somme des parts vaut 1, mais un total de `flexGrow`
              // inférieur à 1 ne distribuerait qu'une fraction de la largeur.
              flex: slice.ratio * 100,
              // Un passage très court reste visible plutôt que de disparaître.
              minWidth: 3,
              borderRadius: Radius.pill,
              borderCurve: 'continuous',
              backgroundColor: ramp[slice.zone - 1],
            }}
          />
        ))}
      </View>

      <View style={{ gap: 10 }}>
        {distribution.slices.map((slice) => (
          <View key={slice.zone} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View
              style={{
                width: 10,
                height: 10,
                borderRadius: Radius.pill,
                backgroundColor: ramp[slice.zone - 1],
              }}
            />
            <Text style={{ ...Type.body, color: theme.text }} numberOfLines={1}>
              {slice.label}
            </Text>
            <Text style={{ ...Type.caption, color: theme.textMuted }} numberOfLines={1}>
              {rangeLabel(slice)}
            </Text>
            <View style={{ flex: 1 }} />
            <Text
              style={{ ...Type.label, color: theme.text, fontVariant: ['tabular-nums'] }}>
              {formatDuration(slice.seconds)}
            </Text>
            <Text
              style={{
                ...Type.caption,
                color: theme.textSecondary,
                fontVariant: ['tabular-nums'],
                width: 36,
                textAlign: 'right',
              }}>
              {Math.round(slice.ratio * 100)} %
            </Text>
          </View>
        ))}
      </View>

      <Text style={{ ...Type.caption, color: theme.textSecondary }}>
        {`Surtout en zone ${dominant.zone} (${dominant.label.toLowerCase()}) — ${Math.round(dominant.ratio * 100)} % du temps avec la ceinture.`}
      </Text>
    </Card>
  );
}
