import { Text, View } from 'react-native';

import { Card } from '@/components/card';
import { HrZoneColors, Radius, Type } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/hooks/use-theme';
import { formatDuration, formatDurationShort } from '@/lib/format';
import { dominantZone, type ZoneDistribution, type ZoneSlice } from '@/lib/hr-zones';

function rangeLabel(slice: ZoneSlice): string {
  if (slice.maxBpm == null) return `${slice.minBpm}+`;
  if (slice.minBpm <= 0) return `< ${slice.maxBpm + 1}`;
  return `${slice.minBpm}–${slice.maxBpm}`;
}

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
              flex: slice.ratio * 100,
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
