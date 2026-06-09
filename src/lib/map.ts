// Configuration de la carte : fond de carte en ligne, OPT-IN.
//
// Par défaut, AUCUN fond de carte n'est chargé : le tracé s'affiche sur fond uni
// (rendu SVG, 100 % hors-ligne, aucune donnée envoyée). L'utilisateur peut
// activer un fond de carte en ligne, au choix :
//   - OpenFreeMap (gratuit, open source, sans clé API) — proposé par défaut ;
//   - sa propre URL de style MapLibre (serveur de tuiles auto-hébergé).
//
// Une fois activé, les requêtes de tuiles (zone du parcours + IP) sortent vers
// le serveur choisi. C'est pourquoi le défaut reste hors-ligne.
import { getSetting, setSetting } from '@/lib/db';

const KEY = 'map_style_url';

/**
 * Style public OpenFreeMap (gratuit, open source, sans clé API, données
 * OpenStreetMap). Proposé en un tap pour activer un vrai fond de carte sans
 * dépendre d'un service payant ni d'un serveur personnel. Auto-hébergeable.
 * @see https://openfreemap.org
 */
export const OPENFREEMAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

/**
 * URL du style MapLibre configurée, ou chaîne vide si le fond de carte en ligne
 * est désactivé (défaut). Vide → le tracé bascule sur le rendu SVG sans réseau.
 */
export async function getMapStyleUrl(): Promise<string> {
  // Re-valide à la lecture (défense de profondeur). Cette clé est désormais
  // exclue de la restauration de sauvegarde (BACKUP_EXCLUDED_KEYS) pour qu'une
  // sauvegarde falsifiée ne puisse pas y injecter un hôte arbitraire ; on
  // re-valide tout de même ici pour neutraliser toute valeur héritée/incorrecte
  // (http://, etc.) en retombant sur le rendu SVG hors-ligne.
  const url = (await getSetting(KEY)) ?? '';
  return isValidMapStyleUrl(url) ? url : '';
}

/**
 * Vrai si l'URL de style est acceptable : vide (carte désactivée) ou HTTPS.
 * Le HTTP en clair est refusé — sinon les requêtes de tuiles (zone du parcours
 * + IP de l'appareil) partiraient en clair ; et un fond http « marcherait » en
 * debug mais échouerait silencieusement en release (cleartext bloqué).
 */
export function isValidMapStyleUrl(url: string): boolean {
  const u = url.trim();
  return u === '' || /^https:\/\//i.test(u);
}

export async function setMapStyleUrl(url: string): Promise<void> {
  const trimmed = url.trim();
  if (!isValidMapStyleUrl(trimmed)) {
    throw new Error('URL de style invalide : HTTPS requis.');
  }
  await setSetting(KEY, trimmed);
}

/**
 * Mention d'attribution à afficher sur la carte. **Obligatoire** dès qu'un fond
 * de carte est affiché : les tuiles dérivent des données OpenStreetMap (et,
 * pour le style public, d'OpenFreeMap / OpenMapTiles).
 */
export function mapAttribution(styleUrl: string): string {
  if (styleUrl.includes('openfreemap')) {
    return '© OpenFreeMap · OpenMapTiles · OpenStreetMap';
  }
  return '© OpenStreetMap';
}
