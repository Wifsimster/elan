// Tests des aides « semaine » (lib/week.ts) : début de semaine (lundi), clé de
// jour locale et agrégation des durées en barres. Pur. Les dates sont
// construites en heure locale pour rester indépendant du fuseau de la CI.
import { dailyDurationBars, localDayKey, startOfWeekMs } from '@/lib/week';

describe('startOfWeekMs', () => {
  it('renvoie le lundi minuit (local) de la semaine', () => {
    // mercredi 11 juin 2025, 14:30 local
    const now = new Date(2025, 5, 11, 14, 30).getTime();
    const monday = new Date(startOfWeekMs(now));
    expect(monday.getFullYear()).toBe(2025);
    expect(monday.getMonth()).toBe(5);
    expect(monday.getDate()).toBe(9); // lundi 9 juin
    expect(monday.getDay()).toBe(1); // lundi
    expect(monday.getHours()).toBe(0);
    expect(monday.getMinutes()).toBe(0);
  });

  it('un lundi se renvoie lui-même (minuit)', () => {
    const monday = new Date(2025, 5, 9, 8, 0).getTime();
    const start = new Date(startOfWeekMs(monday));
    expect(start.getDate()).toBe(9);
    expect(start.getHours()).toBe(0);
  });

  it('un dimanche renvoie le lundi précédent', () => {
    const sunday = new Date(2025, 5, 15, 23, 0).getTime(); // dimanche
    const start = new Date(startOfWeekMs(sunday));
    expect(start.getDate()).toBe(9); // lundi 9
    expect(start.getDay()).toBe(1);
  });
});

describe('localDayKey', () => {
  it('formate en YYYY-MM-DD avec zéro initial', () => {
    expect(localDayKey(new Date(2025, 0, 5))).toBe('2025-01-05');
    expect(localDayKey(new Date(2025, 11, 31))).toBe('2025-12-31');
  });
});

describe('dailyDurationBars', () => {
  const now = new Date(2025, 5, 11, 10, 0).getTime(); // mercredi 11 juin

  it('produit `days` barres, aujourd’hui en dernier', () => {
    const bars = dailyDurationBars([], 7, now);
    expect(bars).toHaveLength(7);
    expect(bars.every((b) => b.value === 0)).toBe(true);
    // aujourd'hui = mercredi -> dernière barre labellisée 'M'
    expect(bars[6].label).toBe('M');
  });

  it('apparie les durées par clé de jour locale', () => {
    const daily = [
      { day: localDayKey(new Date(2025, 5, 11)), durationSec: 1800 }, // aujourd'hui
      { day: localDayKey(new Date(2025, 5, 9)), durationSec: 3600 }, // lundi
    ];
    const bars = dailyDurationBars(daily, 7, now);
    expect(bars[6].value).toBe(1800); // mercredi (dernier)
    expect(bars[4].value).toBe(3600); // lundi (5 jours plus tôt -> index 4 sur 7)
    expect(bars[0].label).toBe('J'); // 6 jours avant mercredi = jeudi précédent
  });

  it('ignore les jours hors fenêtre', () => {
    const daily = [{ day: localDayKey(new Date(2025, 5, 1)), durationSec: 9999 }];
    const bars = dailyDurationBars(daily, 7, now);
    expect(bars.every((b) => b.value === 0)).toBe(true);
  });
});
