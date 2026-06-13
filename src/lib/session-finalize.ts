// Effets de bord best-effort déclenchés APRÈS l'enregistrement local d'une
// séance terminée. Partagé par velo.tsx et muscu.tsx pour leur garantir un
// comportement identique : la séance en base reste la source de vérité, ces
// effets sont optionnels (opt-in) et ne doivent jamais bloquer la navigation.

import { autoBackup } from '@/lib/backup';
import { exportSessionToHealthConnect, type HealthSessionData } from '@/lib/health-connect';

/**
 * Lance les effets post-enregistrement : sauvegarde homelab S3 (si configurée)
 * et miroir Android Health Connect (si activé). Les deux sont « fire-and-forget »
 * et avalent leurs erreurs en interne — aucun `await`, aucun rejet propagé.
 */
export function finalizeSavedSession(data: HealthSessionData): void {
  autoBackup(); // sauvegarde homelab best-effort (ne bloque pas la navigation)
  // Miroir Health Connect (opt-in) : best-effort, ne bloque pas la navigation.
  exportSessionToHealthConnect(data);
}
