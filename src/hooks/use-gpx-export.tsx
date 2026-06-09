// Export d'une sortie vélo en fichier GPX partageable (format Strava).
// Construit le GPX depuis la séance + ses points GPS, l'écrit dans le cache,
// puis ouvre la feuille de partage du système — l'utilisateur choisit la
// destination (upload Strava, Drive, mail…). Aucun appel réseau : le partage
// est délégué à l'OS, fidèle à la promesse 100 % hors-ligne de l'app.
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useCallback, useState } from 'react';

import { getTrackPoints } from '@/lib/db';
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
      const points = await getTrackPoints(session.id);
      if (points.length < 2) {
        setError('Aucun tracé GPS à exporter pour cette sortie.');
        return;
      }
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
      // Le GPX contient le tracé GPS complet : on ne le laisse pas traîner dans
      // le cache après le partage.
      if (file?.exists) file.delete();
      setExporting(false);
    }
  }, []);

  return { exportRide, exporting, error };
}
