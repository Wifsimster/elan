// Brouillon de séance muscu « en pause » : permet de quitter une séance sans la
// terminer, puis de la reprendre plus tard avec tout son état (exercices,
// séries cochées, chrono, échantillons FC). 100 % local : un blob JSON dans la
// table `settings`, donc aucune migration de schéma et rien sur le réseau.

import { deleteSetting, getSetting, setSetting } from '@/lib/db';
import type { HrSample } from '@/lib/types';

const SETTING_KEY = 'muscu_draft';

/** Snapshot sérialisable d'une séance muscu en cours. `E` = forme d'un exercice. */
export type MuscuDraft<E = unknown> = {
  version: 1;
  /** Horodatage de début de séance (ms epoch), conservé tel quel à la reprise. */
  startedAt: number;
  /** Temps actif écoulé en secondes au moment de la sauvegarde. */
  elapsedSec: number;
  exercises: E[];
  hrSamples: HrSample[];
};

export async function saveMuscuDraft(draft: MuscuDraft): Promise<void> {
  await setSetting(SETTING_KEY, JSON.stringify(draft));
}

export async function loadMuscuDraft(): Promise<MuscuDraft | null> {
  const raw = await getSetting(SETTING_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (
      parsed != null &&
      parsed.version === 1 &&
      typeof parsed.startedAt === 'number' &&
      typeof parsed.elapsedSec === 'number' &&
      Array.isArray(parsed.exercises)
    ) {
      return {
        version: 1,
        startedAt: parsed.startedAt,
        elapsedSec: parsed.elapsedSec,
        exercises: parsed.exercises,
        hrSamples: Array.isArray(parsed.hrSamples) ? parsed.hrSamples : [],
      };
    }
  } catch {
    // brouillon corrompu : on l'ignore (repart sur une séance vierge)
  }
  return null;
}

export async function clearMuscuDraft(): Promise<void> {
  await deleteSetting(SETTING_KEY);
}

export async function hasMuscuDraft(): Promise<boolean> {
  return (await loadMuscuDraft()) != null;
}
