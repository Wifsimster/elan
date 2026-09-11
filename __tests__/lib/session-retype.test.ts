// Tests du changement de type d'une séance enregistrée (lib/session-retype.ts).
// Cœur pur : décider si le changement est permis, et recalculer ce qui dépend du
// type. L'écriture en base (db.changeSessionType) n'est pas couverte ici.
import { canRetype, retypeChanges } from '@/lib/session-retype';
import type { ActivityType, Session } from '@/lib/types';

const PROFILE = { weightKg: 70, maxHr: 190 };

/** Sortie d'une heure, 20 km, 20 km/h de moyenne — de quoi comparer les barèmes. */
const session = (overrides: Partial<Session> = {}): Session => ({
  id: 1,
  type: 'velo',
  startedAt: 1_700_000_000_000,
  endedAt: 1_700_003_600_000,
  durationSec: 3600,
  movingTimeSec: 3600,
  notes: null,
  avgHr: null,
  maxHr: null,
  distanceM: 20_000,
  avgSpeedKmh: 20,
  maxSpeedKmh: 30,
  elevationGainM: 100,
  avgCadence: 85,
  maxCadence: 110,
  calories: 500,
  source: null,
  externalId: null,
  ...overrides,
});

describe('canRetype', () => {
  it('autorise les échanges entre activités tracées', () => {
    expect(canRetype('velo', 'marche')).toBe(true);
    expect(canRetype('velo', 'course')).toBe(true);
    expect(canRetype('marche', 'course')).toBe(true);
    expect(canRetype('course', 'velo')).toBe(true);
  });

  it('refuse la musculation dans les deux sens', () => {
    expect(canRetype('muscu', 'velo')).toBe(false);
    expect(canRetype('velo', 'muscu')).toBe(false);
    expect(canRetype('muscu', 'course')).toBe(false);
  });

  it('refuse un changement vers le type courant', () => {
    for (const t of ['velo', 'course', 'marche', 'muscu'] as ActivityType[]) {
      expect(canRetype(t, t)).toBe(false);
    }
  });
});

describe('retypeChanges', () => {
  it('renvoie null quand le changement n’est pas permis', () => {
    expect(retypeChanges(session(), 'velo', PROFILE)).toBeNull();
    expect(retypeChanges(session(), 'muscu', PROFILE)).toBeNull();
    expect(retypeChanges(session({ type: 'muscu' }), 'velo', PROFILE)).toBeNull();
  });

  it('porte le nouveau type', () => {
    expect(retypeChanges(session(), 'marche', PROFILE)!.type).toBe('marche');
  });

  it('ré-estime les calories avec le barème de la nouvelle activité', () => {
    const s = session();
    const versCourse = retypeChanges(s, 'course', PROFILE)!;
    const versMarche = retypeChanges(s, 'marche', PROFILE)!;
    // À 20 km/h : courir coûte beaucoup plus que pédaler, marcher (extrapolation
    // plate au-delà de la table) reste au-dessus du vélo mais loin de la course.
    expect(versCourse.calories).toBeGreaterThan(s.calories!);
    expect(versCourse.calories).toBeGreaterThan(versMarche.calories);
    // La valeur stockée d'origine n'entre jamais dans le nouveau calcul.
    const memeSeanceAutreCalories = session({ calories: 99_999 });
    expect(retypeChanges(memeSeanceAutreCalories, 'course', PROFILE)!.calories).toBeCloseTo(
      versCourse.calories,
      5,
    );
  });

  it('efface la cadence dès qu’on passe à pied', () => {
    for (const to of ['course', 'marche'] as ActivityType[]) {
      const changes = retypeChanges(session(), to, PROFILE)!;
      expect(changes.avgCadence).toBeNull();
      expect(changes.maxCadence).toBeNull();
    }
  });

  it('conserve la cadence en revenant au vélo', () => {
    const s = session({ type: 'course', avgCadence: 80, maxCadence: 95 });
    const changes = retypeChanges(s, 'velo', PROFILE)!;
    expect(changes.avgCadence).toBe(80);
    expect(changes.maxCadence).toBe(95);
  });

  it('compte le temps EN MOUVEMENT quand il est connu', () => {
    const avecArrets = session({ durationSec: 7200, movingTimeSec: 3600 });
    const sansArrets = session({ durationSec: 3600, movingTimeSec: null });
    expect(retypeChanges(avecArrets, 'course', PROFILE)!.calories).toBeCloseTo(
      retypeChanges(sansArrets, 'course', PROFILE)!.calories,
      5,
    );
  });

  it('retombe sur la durée totale sans temps en mouvement', () => {
    const court = session({ durationSec: 1800, movingTimeSec: null });
    const long = session({ durationSec: 3600, movingTimeSec: null });
    expect(retypeChanges(long, 'course', PROFILE)!.calories).toBeGreaterThan(
      retypeChanges(court, 'course', PROFILE)!.calories,
    );
  });

  it('utilise la FC max du PROFIL, pas celle de la séance', () => {
    // La FC max de la séance est une mesure ; celle du profil est la référence
    // d'intensité du modèle cardio. Les confondre fausserait l'estimation.
    const a = session({ avgHr: 150, maxHr: 175 });
    const b = session({ avgHr: 150, maxHr: 200 });
    expect(retypeChanges(a, 'course', PROFILE)!.calories).toBeCloseTo(
      retypeChanges(b, 'course', PROFILE)!.calories,
      5,
    );

    const profilPlusHaut = { weightKg: 70, maxHr: 210 };
    expect(retypeChanges(a, 'course', profilPlusHaut)!.calories).not.toBeCloseTo(
      retypeChanges(a, 'course', PROFILE)!.calories,
      5,
    );
  });

  it('tient compte du poids du profil', () => {
    const leger = retypeChanges(session(), 'course', { weightKg: 55, maxHr: 190 })!;
    const lourd = retypeChanges(session(), 'course', { weightKg: 95, maxHr: 190 })!;
    expect(lourd.calories).toBeGreaterThan(leger.calories);
  });
});
