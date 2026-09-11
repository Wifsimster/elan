// Choix d'une encre lisible sur un aplat de couleur.
//
// Les puces sélectionnées peignent leur fond avec la teinte de l'activité : du
// blanc y est illisible dès que la teinte est claire (le lime de la marche, le
// teal du vélo tombent sous 2:1). Plutôt que de maintenir une liste de « teintes
// claires » à la main, on mesure. Pur et sans dépendance au thème, donc testé.

/** Canaux 0-255 d'un hex `#rrggbb` (ou `#rgb`). `null` si la chaîne est invalide. */
function channels(hex: string): [number, number, number] | null {
  const clean = hex.trim().replace(/^#/, '');
  const full =
    clean.length === 3
      ? clean
          .split('')
          .map((c) => c + c)
          .join('')
      : clean;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null;
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ];
}

/** Luminance relative WCAG 2.x d'une couleur opaque, entre 0 (noir) et 1 (blanc). */
export function relativeLuminance(hex: string): number {
  const rgb = channels(hex);
  if (!rgb) return 0;
  const [r, g, b] = rgb.map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Rapport de contraste WCAG entre deux couleurs opaques, de 1:1 à 21:1. */
export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const [hi, lo] = la >= lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

/**
 * Encre la plus lisible sur `background` parmi `candidates` (à égalité, la
 * première l'emporte). On choisit toujours la meilleure des deux plutôt que de
 * comparer à un seuil : sur une teinte moyenne, aucune ne passerait le seuil et
 * il faudrait quand même en prendre une.
 */
export function bestInk(background: string, candidates: readonly string[]): string {
  let best = candidates[0];
  let bestRatio = -1;
  for (const ink of candidates) {
    const ratio = contrastRatio(background, ink);
    if (ratio > bestRatio) {
      best = ink;
      bestRatio = ratio;
    }
  }
  return best;
}
