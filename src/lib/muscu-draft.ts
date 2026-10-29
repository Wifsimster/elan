import { deleteSetting, getSetting, setSetting } from '@/lib/db';
import type { HrSample } from '@/lib/types';

const SETTING_KEY = 'muscu_draft';

export type MuscuDraft<E = unknown> = {
  version: 1;
  startedAt: number;
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
  }
  return null;
}

export async function clearMuscuDraft(): Promise<void> {
  await deleteSetting(SETTING_KEY);
}

export async function hasMuscuDraft(): Promise<boolean> {
  return (await loadMuscuDraft()) != null;
}
