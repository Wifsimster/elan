// Tests des fonctions pures des rappels de séance : contenu de la notification
// (describeToday) et conversion d'index de planning vers le `weekday`
// d'expo-notifications. On mocke expo-notifications : le module est importé pour
// ses helpers purs, on ne veut pas charger le module natif sous Jest.
jest.mock('expo-notifications', () => ({}));

import { describeToday, planIndexToWeekday } from '@/lib/notifications';
import { templateById, type PlannedSession } from '@/lib/program';

describe('planIndexToWeekday', () => {
  // Planning : 0 = lundi … 6 = dimanche.
  // expo-notifications : 1 = dimanche, 2 = lundi … 7 = samedi.
  it('lundi (0) → 2', () => {
    expect(planIndexToWeekday(0)).toBe(2);
  });

  it('vendredi (4) → 6', () => {
    expect(planIndexToWeekday(4)).toBe(6);
  });

  it('samedi (5) → 7', () => {
    expect(planIndexToWeekday(5)).toBe(7);
  });

  it('dimanche (6) → 1 (et non 8)', () => {
    expect(planIndexToWeekday(6)).toBe(1);
  });

  it('les 7 jours couvrent exactement weekday 1..7 sans doublon', () => {
    const weekdays = [0, 1, 2, 3, 4, 5, 6].map(planIndexToWeekday).sort((a, b) => a - b);
    expect(weekdays).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });
});

describe('describeToday', () => {
  it('renvoie null les jours de repos (aucune notification)', () => {
    expect(describeToday({ kind: 'repos' })).toBeNull();
  });

  it('vélo : rappel de préparer le vélo et la ceinture', () => {
    const content = describeToday({ kind: 'velo', label: 'Vélo 1h' });
    expect(content).not.toBeNull();
    expect(content?.title).toBe("Aujourd'hui : Vélo 1h");
    expect(content?.body).toContain('vélo');
  });

  it('muscu : le corps liste les exercices du programme du jour', () => {
    const plan: PlannedSession = {
      kind: 'muscu',
      label: 'Full-body A',
      templateId: 'fullbody-a',
    };
    const content = describeToday(plan);
    expect(content).not.toBeNull();
    expect(content?.title).toBe("Aujourd'hui : Full-body A");
    expect(content?.body.startsWith('Au programme : ')).toBe(true);

    // Tous les exercices du template doivent figurer dans le rappel.
    const names = templateById('fullbody-a')!.exercises.map((e) => e.name);
    expect(names.length).toBeGreaterThan(0);
    for (const name of names) {
      expect(content?.body).toContain(name);
    }
  });

  it('muscu : repli sur le message générique si le template est introuvable', () => {
    // Plan corrompu : templateId inexistant (cast volontaire pour le test).
    const plan = {
      kind: 'muscu',
      label: 'Séance',
      templateId: 'inexistant',
    } as unknown as PlannedSession;
    const content = describeToday(plan);
    expect(content?.body).toBe('Pense à sortir les haltères.');
  });
});
