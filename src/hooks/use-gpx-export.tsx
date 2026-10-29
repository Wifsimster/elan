import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useCallback, useState } from 'react';

import { getTrackPoints } from '@/lib/db';
import { getPrivacyZoneM, trimPrivacyZone } from '@/lib/privacy';
import { buildRideGpx, rideFileName } from '@/lib/strava/export';
import type { Session } from '@/lib/types';

export function useGpxExport() {
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const exportRide = useCallback(async (session: Session) => {
    setError(null);
    setExporting(true);
    let file: File | null = null;
    try {
      const all = await getTrackPoints(session.id);
      if (all.length < 2) {
        setError('Aucun tracé GPS à exporter pour cette sortie.');
        return;
      }
      const points = trimPrivacyZone(all, await getPrivacyZoneM());
      const gpx = buildRideGpx(session, points);

      file = new File(Paths.cache, rideFileName(session));
      if (file.exists) file.delete();
      file.create();
      file.write(gpx);

      if (!(await Sharing.isAvailableAsync())) {
        setError("Le partage n'est pas disponible sur cet appareil.");
        return;
      }
      await Sharing.shareAsync(file.uri, {
        mimeType: 'application/gpx+xml',
        UTI: 'com.topografix.gpx',
        dialogTitle: 'Exporter en GPX (Strava)',
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Export GPX impossible.');
    } finally {
      if (file?.exists) file.delete();
      setExporting(false);
    }
  }, []);

  return { exportRide, exporting, error };
}
