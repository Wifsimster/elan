// Tests pour les helpers de formatage (durée, distance, vitesse, FC, date — FR).
import {
  cadenceParts,
  caloriesParts,
  distanceParts,
  elevationParts,
  formatCalories,
  formatDateShort,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatDurationShort,
  formatHr,
  formatSpeed,
  hrParts,
  speedParts,
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

describe('scission valeur / unité (Measure)', () => {
  it('distanceParts sépare le chiffre de l’unité', () => {
    expect(distanceParts(12_400)).toEqual({ value: '12,4', unit: 'km' });
    expect(distanceParts(850)).toEqual({ value: '850', unit: 'm' });
  });

  it('valeur inconnue → pas d’unité (« — » seul, sans retour à la ligne)', () => {
    expect(distanceParts(null)).toEqual({ value: '—' });
    expect(speedParts(null)).toEqual({ value: '—' });
    expect(hrParts(undefined)).toEqual({ value: '—' });
    expect(caloriesParts(null)).toEqual({ value: '—' });
    expect(cadenceParts(null)).toEqual({ value: '—' });
  });

  it('speedParts utilise la virgule décimale (FR)', () => {
    expect(speedParts(18.27)).toEqual({ value: '18,3', unit: 'km/h' });
  });

  it('hrParts / caloriesParts arrondissent', () => {
    expect(hrParts(72.6)).toEqual({ value: '73', unit: 'bpm' });
    expect(caloriesParts(412.6)).toEqual({ value: '413', unit: 'kcal' });
  });

  it('elevationParts reste en mètres et tolère null (→ 0)', () => {
    expect(elevationParts(57)).toEqual({ value: '57', unit: 'm' });
    expect(elevationParts(null)).toEqual({ value: '0', unit: 'm' });
  });

  it('cadenceParts arrondit les tr/min', () => {
    expect(cadenceParts(84.4)).toEqual({ value: '84', unit: 'tr/min' });
  });

  it('les formateurs-chaîne restent cohérents avec leurs parts', () => {
    expect(formatDistance(12_400)).toBe('12,4 km');
    expect(formatSpeed(18.27)).toBe('18,3 km/h');
    expect(formatHr(72.6)).toBe('73 bpm');
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
