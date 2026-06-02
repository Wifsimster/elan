// Configuration de la carte : URL du style MapLibre auto-hébergé (homelab).
// Par défaut on pointe sur le serveur de tuiles perso ; vidé manuellement,
// l'app retombe sur le tracé SVG sans fond.
import { getSetting, setSetting } from '@/lib/db';

const KEY = 'map_style_url';

/** Style MapLibre auto-hébergé utilisé par défaut (homelab). */
export const DEFAULT_MAP_STYLE_URL =
  'https://tiles.battistella.ovh/styles/osm-bright/style.json';

/** URL du style MapLibre. Retombe sur le serveur perso si aucune valeur enregistrée. */
export async function getMapStyleUrl(): Promise<string> {
  const stored = await getSetting(KEY);
  return stored ?? DEFAULT_MAP_STYLE_URL;
}

export async function setMapStyleUrl(url: string): Promise<void> {
  await setSetting(KEY, url.trim());
}
