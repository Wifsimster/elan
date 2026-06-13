import { ScrollView, Text } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BackupCard } from '@/components/settings/backup-card';
import { CadenceSensorCard } from '@/components/settings/cadence-sensor-card';
import { DataCard } from '@/components/settings/data-card';
import { DataExportCard } from '@/components/settings/data-export-card';
import { HealthConnectCard } from '@/components/settings/health-connect-card';
import { HeartRateCard } from '@/components/settings/heart-rate-card';
import { MapCard } from '@/components/settings/map-card';
import { NotificationsCard } from '@/components/settings/notifications-card';
import { ProfileCard } from '@/components/settings/profile-card';
import { StravaImportCard } from '@/components/settings/strava-import-card';
import { WeekPlanCard } from '@/components/settings/week-plan-card';
import { Type } from '@/constants/theme';
import { useScreenContentStyle } from '@/hooks/use-screen-layout';
import { useTheme } from '@/hooks/use-theme';

/**
 * Écran Réglages : se contente d'assembler les cartes de réglages, chacune
 * autonome (état + effets + accès `lib/` dans son propre composant, sous
 * `components/settings/`). L'ordre d'affichage est défini ici.
 */
export default function SettingsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const contentStyle = useScreenContentStyle();

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        ...contentStyle,
        paddingTop: insets.top + 12,
        paddingBottom: 40,
        gap: 16,
      }}>
      <Text style={{ ...Type.title, color: theme.text }}>Réglages</Text>

      <HeartRateCard />
      <CadenceSensorCard />
      <MapCard />
      {/* Health Connect : Android uniquement (la carte renvoie null ailleurs). */}
      <HealthConnectCard />
      <ProfileCard />
      <WeekPlanCard />
      <NotificationsCard />
      <DataCard />
      <DataExportCard />
      <StravaImportCard />
      <BackupCard />
    </ScrollView>
  );
}
