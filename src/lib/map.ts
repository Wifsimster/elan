// Configuration de la carte : URL du style MapLibre auto-hébergé (homelab).
// Tant qu'aucune URL n'est définie, l'app retombe sur le tracé SVG sans fond.
import { getSetting, setSetting } from '@/lib/db';

const KEY = 'map_style_url';

/** URL du style MapLibre, ex. https://tiles.battistella.ovh/styles/basic/style.json. */
export async function getMapStyleUrl(): Promise<string> {
  return (await getSetting(KEY)) ?? '';
}

export async function setMapStyleUrl(url: string): Promise<void> {
  await setSetting(KEY, url.trim());
}
