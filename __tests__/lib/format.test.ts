// Tests pour les helpers de formatage (durée, distance, vitesse, FC, date — FR).
import {
  formatCalories,
  formatDateShort,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatDurationShort,
  formatHr,
  formatSpeed,
} from '@/lib/format';

describe('formatDuration', () => {
  it('formate m:ss sous une heure', () => {
    expect(formatDuration(125)).toBe('2:05');
  });

  it('formate h:mm:ss au-delà d’une heure', () => {
    expect(formatDuration(3725)).toBe('1:02:05');
  });

  it('renvoie 0:00 pour 0 ou valeur négative', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(-5)).toBe('0:00');
  });

  it('tronque les fractions de secondes', () => {
    expect(formatDuration(59.9)).toBe('0:59');
  });

  it('padde les minutes et secondes à 2 chiffres', () => {
    expect(formatDuration(3601)).toBe('1:00:01');
  });
});

describe('formatDurationShort', () => {
  it('moins d’une heure → "M min"', () => {
    expect(formatDurationShort(45 * 60)).toBe('45 min');
  });

  it('au-delà d’une heure → "H h MM"', () => {
    expect(formatDurationShort(3720)).toBe('1 h 02');
  });

  it('0 secondes → "0 min"', () => {
    expect(formatDurationShort(0)).toBe('0 min');
  });

  it('reporte la retenue des minutes au lieu de produire "H h 60"', () => {
    // 1 h 59 min 30 s arrondit à 2 h pile (et non « 1 h 60 »).
    expect(formatDurationShort(7170)).toBe('2 h 00');
    expect(formatDurationShort(7189)).toBe('2 h 00');
    // Juste sous le seuil de report : reste « 1 h 59 ».
    expect(formatDurationShort(7169)).toBe('1 h 59');
  });
});

describe('formatDistance', () => {
  it('null/undefined → "—"', () => {
    expect(formatDistance(null)).toBe('—');
    expect(formatDistance(undefined)).toBe('—');
  });

  it('< 1 km → "N m"', () => {
    expect(formatDistance(850)).toBe('850 m');
    expect(formatDistance(0)).toBe('0 m');
  });

  it('≥ 1 km → "N,N km" avec virgule décimale (FR)', () => {
    expect(formatDistance(12_400)).toBe('12,4 km');
    expect(formatDistance(1_000)).toBe('1,0 km');
  });

  it('arrondit les mètres', () => {
    expect(formatDistance(123.4)).toBe('123 m');
  });
});

describe('formatSpeed', () => {
  it('null → "—"', () => {
    expect(formatSpeed(null)).toBe('—');
  });

  it('1 décimale avec virgule', () => {
    expect(formatSpeed(23.5)).toBe('23,5 km/h');
    expect(formatSpeed(0)).toBe('0,0 km/h');
    expect(formatSpeed(18.27)).toBe('18,3 km/h');
  });
});

describe('formatHr', () => {
  it('null → "—"', () => {
    expect(formatHr(null)).toBe('—');
  });

  it('arrondit en bpm', () => {
    expect(formatHr(72.4)).toBe('72 bpm');
    expect(formatHr(72.6)).toBe('73 bpm');
  });
});

describe('formatCalories', () => {
  it('null → "—"', () => {
    expect(formatCalories(null)).toBe('—');
  });

  it('arrondit en kcal', () => {
    expect(formatCalories(412.6)).toBe('413 kcal');
  });
});

describe('formatDateTime / formatDateShort', () => {
  // 2 juin 2025 (lundi) à 18:30 — fixe par construction d’une Date locale.
  const ms = new Date(2025, 5, 2, 18, 30, 0).getTime();

  it('formatDateTime concatène jour, date, mois, heure', () => {
    expect(formatDateTime(ms)).toBe('lun. 2 juin, 18:30');
  });

  it('formatDateShort renvoie "jour mois" abrégé', () => {
    expect(formatDateShort(ms)).toBe('2 juin');
  });

  it('padde l’heure à 2 chiffres', () => {
    const tot = new Date(2025, 0, 1, 9, 5, 0).getTime(); // mer. 1 janv., 09:05
    expect(formatDateTime(tot)).toBe('mer. 1 janv., 09:05');
  });
});
