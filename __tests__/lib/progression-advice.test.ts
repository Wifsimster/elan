import {
  adviceLabel,
  difficultyLabel,
  difficultyScore,
  suggestProgression,
} from '@/lib/progression-advice';

describe('difficultyScore', () => {
  it('mappe chaque ressenti sur un score ordonnable, null sinon', () => {
    expect(difficultyScore('facile')).toBe(1);
    expect(difficultyScore('moyen')).toBe(2);
    expect(difficultyScore('dur')).toBe(3);
    expect(difficultyScore(null)).toBeNull();
    expect(difficultyScore(undefined)).toBeNull();
  });
});

describe('difficultyLabel', () => {
  it('libelle chaque ressenti en français', () => {
    expect(difficultyLabel('facile')).toBe('Facile');
    expect(difficultyLabel('moyen')).toBe('Moyen');
    expect(difficultyLabel('dur')).toBe('Dur');
  });
});

describe('suggestProgression', () => {
  it('aucune note exploitable : maintiens', () => {
    expect(suggestProgression([])).toBe('maintiens');
    expect(suggestProgression([null, undefined])).toBe('maintiens');
  });

  it('deux séances faciles : augmente', () => {
    expect(suggestProgression(['facile', 'facile'])).toBe('augmente');
  });

  it('dernière facile sans dur récent : augmente', () => {
    expect(suggestProgression(['moyen', 'facile'])).toBe('augmente');
  });

  it('dernière dure : réduis (priorité sur la moyenne)', () => {
    expect(suggestProgression(['facile', 'dur'])).toBe('reduis');
  });

  it('moyen dominant : maintiens', () => {
    expect(suggestProgression(['moyen', 'moyen', 'moyen'])).toBe('maintiens');
  });

  it('deux séances dures : réduis', () => {
    expect(suggestProgression(['dur', 'dur'])).toBe('reduis');
  });

  it('ignore les séances non notées', () => {
    expect(suggestProgression([null, 'facile', null, 'facile'])).toBe('augmente');
  });

  it('respecte la fenêtre : un « dur » ancien sort du calcul', () => {
    expect(suggestProgression(['dur', 'facile', 'facile'], 2)).toBe('augmente');
    expect(suggestProgression(['dur', 'facile', 'facile'], 3)).toBe('maintiens');
  });
});

describe('adviceLabel', () => {
  it('formule chaque conseil au tutoiement', () => {
    expect(adviceLabel('augmente')).toBe('Augmente les reps ou la charge');
    expect(adviceLabel('reduis')).toBe('Réduis la charge, soigne la technique');
    expect(adviceLabel('maintiens')).toBe('Maintiens, charge bien calibrée');
  });
});
