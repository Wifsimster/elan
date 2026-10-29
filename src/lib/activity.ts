import type { MaterialCommunityIcons } from '@expo/vector-icons';

import type { ActivityType } from '@/lib/types';

type Meta = {
  label: string;
  shortLabel: string;
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  colorKey: 'velo' | 'muscu' | 'course' | 'marche';
  gps: boolean;
  pace: boolean;
};

export const ACTIVITY_META: Record<ActivityType, Meta> = {
  velo: { label: 'Vélo', shortLabel: 'Vélo', icon: 'bike', colorKey: 'velo', gps: true, pace: false },
  course: {
    label: 'Course à pied',
    shortLabel: 'Course',
    icon: 'run',
    colorKey: 'course',
    gps: true,
    pace: true,
  },
  marche: { label: 'Marche', shortLabel: 'Marche', icon: 'walk', colorKey: 'marche', gps: true, pace: true },
  muscu: {
    label: 'Musculation',
    shortLabel: 'Muscu',
    icon: 'dumbbell',
    colorKey: 'muscu',
    gps: false,
    pace: false,
  },
};

export const ACTIVITY_TYPES: ActivityType[] = ['velo', 'course', 'marche', 'muscu'];

export function isGpsActivity(type: ActivityType): boolean {
  return ACTIVITY_META[type].gps;
}

export function usesPace(type: ActivityType): boolean {
  return ACTIVITY_META[type].pace;
}

export function toActivityType(value: unknown, fallback: ActivityType = 'velo'): ActivityType {
  return ACTIVITY_TYPES.includes(value as ActivityType) ? (value as ActivityType) : fallback;
}
