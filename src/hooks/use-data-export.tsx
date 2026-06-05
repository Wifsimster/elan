// Export des données vers un fichier partageable (bilan Markdown ou JSON brut).
// Écrit dans le cache puis ouvre la feuille de partage du système — l'utilisateur
// choisit la destination (Drive, mail, fichier…) : coach IA, tableur, sauvegarde…
// Aucun appel réseau : le partage est délégué à l'OS.
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useCallback, useState } from 'react';

import { buildCoachJson, buildCoachMarkdown } from '@/lib/coach-export';

export type ExportFormat = 'markdown' | 'json';

const SPECS: Record<ExportFormat, { name: string; mimeType: string; uti: string }> = {
  markdown: { name: 'suivi-sport-coach.md', mimeType: 'text/markdown', uti: 'net.daringfireball.markdown' },
  json: { name: 'suivi-sport-export.json', mimeType: 'application/json', uti: 'public.json' },
};

export function useDataExport() {
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (format: ExportFormat) => {
    setError(null);
    setExporting(format);
    try {
      const spec = SPECS[format];
      const content = format === 'markdown' ? await buildCoachMarkdown() : await buildCoachJson();

      const file = new File(Paths.cache, spec.name);
      if (file.exists) file.delete();
      file.create();
      file.write(content);

      if (!(await Sharing.isAvailableAsync())) {
        setError("Le partage n'est pas disponible sur cet appareil.");
        return;
      }
      await Sharing.shareAsync(file.uri, {
        mimeType: spec.mimeType,
        UTI: spec.uti,
        dialogTitle: 'Exporter mes données',
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Export impossible.');
    } finally {
      setExporting(null);
    }
  }, []);

  return {
    exporting,
    error,
    exportMarkdown: useCallback(() => run('markdown'), [run]),
    exportJson: useCallback(() => run('json'), [run]),
  };
}
