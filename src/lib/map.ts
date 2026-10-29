import { getSetting, setSetting } from '@/lib/db';

const KEY = 'map_style_url';

let cachedStyleUrl: string | undefined;

export function peekMapStyleUrl(): string | undefined {
  return cachedStyleUrl;
}

export const OPENFREEMAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

export async function getMapStyleUrl(): Promise<string> {
  const url = (await getSetting(KEY)) ?? '';
  const resolved = isValidMapStyleUrl(url) ? url : '';
  cachedStyleUrl = resolved;
  return resolved;
}

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
  cachedStyleUrl = trimmed;
}

export function mapAttribution(styleUrl: string): string {
  if (styleUrl.includes('openfreemap')) {
    return '© OpenFreeMap · OpenMapTiles · OpenStreetMap';
  }
  return '© OpenStreetMap';
}
