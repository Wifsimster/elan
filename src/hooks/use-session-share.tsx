// Partage d'une séance en image : capture la carte partageable (rendue hors
// écran) en PNG, puis ouvre la feuille de partage du système — l'utilisateur
// choisit la destination (Discord, etc.). Aucun appel réseau : tout est délégué
// à l'OS, fidèle à la promesse 100 % hors-ligne de l'app.
import * as Sharing from 'expo-sharing';
import { useCallback, useState, type RefObject } from 'react';
import { View } from 'react-native';
import { captureRef } from 'react-native-view-shot';

export function useSessionShare() {
  const [sharing, setSharing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // La ref de la carte est détenue par l'écran (un `useRef` local) et passée
  // ici au moment du partage — jamais lue pendant le rendu.
  const share = useCallback(async (cardRef: RefObject<View | null>) => {
    setError(null);
    setSharing(true);
    try {
      const uri = await captureRef(cardRef, { format: 'png', quality: 1, result: 'tmpfile' });
      if (!(await Sharing.isAvailableAsync())) {
        setError("Le partage n'est pas disponible sur cet appareil.");
        return;
      }
      await Sharing.shareAsync(uri, {
        mimeType: 'image/png',
        UTI: 'public.png',
        dialogTitle: 'Partager la séance',
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Partage impossible.');
    } finally {
      setSharing(false);
    }
  }, []);

  return { share, sharing, error };
}
