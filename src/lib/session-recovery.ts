import {
  deleteSession,
  finalizeSession,
  getTrackPoints,
  listInProgressSessions,
} from '@/lib/db';
import { isGpsActivity } from '@/lib/activity';
import { aggregateFromPoints } from '@/lib/session-aggregate';

export type RecoveryOutcome = {
  recovered: number;
  purged: number;
};

export async function recoverOrphanSessions(): Promise<RecoveryOutcome> {
  const outcome: RecoveryOutcome = { recovered: 0, purged: 0 };
  try {
    const orphans = await listInProgressSessions();
    for (const s of orphans) {
      try {
        if (isGpsActivity(s.type)) {
          const points = await getTrackPoints(s.id);
          const agg = aggregateFromPoints(points);
          if (agg) {
            await finalizeSession(
              s.id,
              {
                endedAt: agg.endedAt,
                durationSec: agg.durationSec,
                movingTimeSec: agg.movingTimeSec,
                distanceM: agg.distanceM,
                avgSpeedKmh: agg.avgSpeedKmh,
                maxSpeedKmh: agg.maxSpeedKmh,
                elevationGainM: agg.elevationGainM,
                avgHr: agg.avgHr,
                maxHr: agg.maxHr,
                avgCadence: agg.avgCadence,
                maxCadence: agg.maxCadence,
                notes: 'Sortie récupérée après interruption',
              },
              points,
            );
            outcome.recovered++;
            continue;
          }
        }
        await deleteSession(s.id);
        outcome.purged++;
      } catch {
      }
    }
  } catch {
  }
  return outcome;
}
