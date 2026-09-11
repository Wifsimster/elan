// Tests du choix d'encre lisible (lib/contrast.ts).
import { bestInk, contrastRatio, relativeLuminance } from '@/lib/contrast';

const BLANC = '#FFFFFF';
const ENCRE_SOMBRE = '#0B0E13'; // `OnBright` du thème
const MARCHE = '#A3E635'; // teinte lime — la plus claire du thème
const VELO = '#22D3C5';
const MUSCU = '#A78BFA';
const ACCENT = '#5B7CFF';

describe('relativeLuminance', () => {
  it('va de 0 (noir) à 1 (blanc)', () => {
    expect(relativeLuminance('#000000')).toBeCloseTo(0, 5);
    expect(relativeLuminance(BLANC)).toBeCloseTo(1, 5);
  });

  it('accepte la forme courte et ignore le dièse', () => {
    expect(relativeLuminance('#fff')).toBeCloseTo(relativeLuminance(BLANC), 5);
    expect(relativeLuminance('FFFFFF')).toBeCloseTo(relativeLuminance(BLANC), 5);
  });

  it('retombe sur 0 pour une chaîne invalide plutôt que NaN', () => {
    expect(relativeLuminance('pas une couleur')).toBe(0);
    expect(relativeLuminance('#12345')).toBe(0);
  });
});

describe('contrastRatio', () => {
  it('vaut 21 entre noir et blanc, 1 pour une couleur avec elle-même', () => {
    expect(contrastRatio('#000000', BLANC)).toBeCloseTo(21, 2);
    expect(contrastRatio(MARCHE, MARCHE)).toBeCloseTo(1, 5);
  });

  it('est symétrique', () => {
    expect(contrastRatio(MARCHE, BLANC)).toBeCloseTo(contrastRatio(BLANC, MARCHE), 5);
  });
});

describe('bestInk', () => {
  const encres = [ENCRE_SOMBRE, BLANC];

  it('pose une encre sombre sur les teintes claires du thème', () => {
    expect(bestInk(MARCHE, encres)).toBe(ENCRE_SOMBRE);
    expect(bestInk(VELO, encres)).toBe(ENCRE_SOMBRE);
  });

  it('garde le blanc sur un fond sombre', () => {
    expect(bestInk('#000000', encres)).toBe(BLANC);
    expect(bestInk('#14181F', encres)).toBe(BLANC); // surface de carte
  });

  it('retient l’encre sombre sur TOUTES les teintes d’activité du thème', () => {
    // Résultat mesuré, pas choisi : même sur le violet muscu ou le bleu accent,
    // que l'app peignait en blanc, le sombre contraste deux fois mieux.
    for (const teinte of [MARCHE, VELO, MUSCU, ACCENT]) {
      expect(bestInk(teinte, encres)).toBe(ENCRE_SOMBRE);
      expect(contrastRatio(teinte, ENCRE_SOMBRE)).toBeGreaterThan(contrastRatio(teinte, BLANC));
    }
  });

  it('le choix retenu passe le seuil AA sur les teintes du thème', () => {
    for (const teinte of [MARCHE, VELO, MUSCU, ACCENT, '#38BDF8', '#FF5C7A']) {
      expect(contrastRatio(teinte, bestInk(teinte, encres))).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('choisit toujours la meilleure des candidates, même sans seuil atteint', () => {
    // Un gris moyen : aucune encre ne passe AA, on prend quand même la meilleure.
    const gris = '#767676';
    const choisie = bestInk(gris, encres);
    const autre = choisie === BLANC ? ENCRE_SOMBRE : BLANC;
    expect(contrastRatio(gris, choisie)).toBeGreaterThanOrEqual(contrastRatio(gris, autre));
  });
});
