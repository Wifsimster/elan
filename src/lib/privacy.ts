// Zone de confidentialité pour les exports GPS.
//
// Un tracé vélo commence et finit presque toujours au domicile (le GPS tourne
// dès le départ devant chez soi) : exporter le tracé brut en pleine précision
// révèle l'adresse du domicile (et, par recoupement, les horaires). Cette zone
// permet de retirer les points situés dans un rayon donné autour du premier et
// du dernier point avant tout export hors-appareil.
//
// Réglage opt-in stocké dans `settings` (rayon en mètres, 0 = désactivé). Le
// rognage n'altère jamais les données stockées : il ne s'applique qu'au moment
// de construire un artefact partagé (GPX, etc.).
import { getSetting, setSetting } from '@/lib/db';
import { haversineMeters } from '@/lib/geo';

const KEY = 'privacy_zone_m';

/** Rayons proposés dans les Réglages (mètres). 0 = aucune zone (tracé complet). */
export const PRIVACY_ZONE_OPTIONS = [0, 100, 200, 500] as const;

/** Rayon de la zone de confidentialité configuré (mètres ; 0 si désactivé). */
export async function getPrivacyZoneM(): Promise<number> {
  const raw = await getSetting(KEY);
  if (raw == null) return 0;
  const v = Number(raw);
  return Number.isFinite(v) && v > 0 ? v : 0;
}

export async function setPrivacyZoneM(meters: number): Promise<void> {
  await setSetting(KEY, String(Math.max(0, Math.round(meters))));
}

/**
 * Retire les points de début et de fin situés à moins de `radiusM` du premier /
 * dernier point (zone de confidentialité : domicile, travail…). Conserve au
 * moins deux points : si le rognage viderait le tracé (boucle entièrement dans
 * la zone), on renvoie le tracé d'origine inchangé plutôt qu'un export vide.
 */
export function trimPrivacyZone<T extends { lat: number; lon: number }>(
  points: T[],
  radiusM: number,
): T[] {
  if (radiusM <= 0 || points.length < 2) return points;
  const first = points[0];
  const last = points[points.length - 1];

  let start = 0;
  while (start < points.length && haversineMeters(first, points[start]) < radiusM) start++;
  let end = points.length - 1;
  while (end > start && haversineMeters(last, points[end]) < radiusM) end--;

  // Garde-fou : il faut ≥ 2 points pour un tracé exploitable.
  if (end - start < 1) return points;
  return points.slice(start, end + 1);
}
