// Import de fichiers Strava (GPX/TCX/FIT, y compris .gz de l'export en masse) :
// sélection, lecture locale, décodage, déduplication et insertion.
// 100 % hors-ligne — aucun appel réseau.
import * as DocumentPicker from 'expo-document-picker';
import { File } from 'expo-file-system';
import { useCallback, useState } from 'react';

import { getProfile, insertImportedSession } from '@/lib/db';
import { buildDrafts } from '@/lib/strava/import';

/** Plafond de taille par fichier (octets) — garde-fou mémoire / DoS. */
const MAX_BYTES = 30 * 1024 * 1024;

export type ImportResult = {
  imported: number;
  duplicates: number;
  skipped: number;
  errors: number;
  /** Détails par fichier/activité (motifs d'ignorés et erreurs). */
  details: string[];
};

export function useStravaImport() {
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pickAndImport = useCallback(async (): Promise<ImportResult | null> => {
    setError(null);

    let picked: DocumentPicker.DocumentPickerResult;
    try {
      picked = await DocumentPicker.getDocumentAsync({
        type: ['application/gpx+xml', 'application/xml', 'text/xml', 'application/octet-stream', '*/*'],
        multiple: true,
        copyToCacheDirectory: true,
      });
    } catch {
      setError('Sélection de fichier impossible.');
      return null;
    }
    if (picked.canceled || !picked.assets?.length) return null;

    setImporting(true);
    const res: ImportResult = { imported: 0, duplicates: 0, skipped: 0, errors: 0, details: [] };
    try {
      const profile = await getProfile();
      for (const asset of picked.assets) {
        try {
          if (asset.size != null && asset.size > MAX_BYTES) {
            res.errors++;
            res.details.push(`${asset.name} : fichier trop volumineux`);
            continue;
          }
          const bytes = await new File(asset.uri).bytes();
          const { drafts, skipped } = buildDrafts(bytes, profile.weightKg);
          for (const reason of skipped) {
            res.skipped++;
            res.details.push(`${asset.name} : ${reason}`);
          }
          for (const draft of drafts) {
            const status = await insertImportedSession(draft.session, draft.points);
            if (status === 'imported') res.imported++;
            else res.duplicates++;
          }
        } catch (e) {
          res.errors++;
          res.details.push(`${asset.name} : ${e instanceof Error ? e.message : 'fichier illisible'}`);
        }
      }
      setResult(res);
      return res;
    } finally {
      setImporting(false);
    }
  }, []);

  return { importing, result, error, pickAndImport };
}
