import { isGpsActivity } from '@/lib/activity';
import { estimateCalories } from '@/lib/calories';
import type { ActivityType, Session } from '@/lib/types';

export function canRetype(from: ActivityType, to: ActivityType): boolean {
  return from !== to && isGpsActivity(from) && isGpsActivity(to);
}

export type RetypeChanges = {
  type: ActivityType;
  calories: number;
  avgCadence: number | null;
  maxCadence: number | null;
};

export function retypeChanges(
  session: Session,
  to: ActivityType,
  profile: { weightKg: number; maxHr: number },
): RetypeChanges | null {
  if (!canRetype(session.type, to)) return null;

  const effectiveSec = session.movingTimeSec ?? session.durationSec;
  const calories = estimateCalories({
    type: to,
    weightKg: profile.weightKg,
    durationSec: effectiveSec,
    avgSpeedKmh: session.avgSpeedKmh,
    elevationGainM: session.elevationGainM,
    avgHr: session.avgHr,
    maxHr: profile.maxHr,
  });

  const keepsCadence = to === 'velo';

  return {
    type: to,
    calories,
    avgCadence: keepsCadence ? session.avgCadence : null,
    maxCadence: keepsCadence ? session.maxCadence : null,
  };
}
