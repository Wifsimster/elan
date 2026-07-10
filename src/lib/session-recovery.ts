// Sauvetage au démarrage des séances laissées « en cours » (endedAt NULL) par un
// crash / kill mémoire en pleine séance.
//
// Le vélo écrit désormais ses points GPS en base au fil de l'eau (flush
// incrémental, use-gps-tracker) et crée sa séance dès le `begin()`. Si l'app est
// tuée avant « Terminer », la séance reste orpheline : au prochain lancement on
// la finalise à partir de ses points survivants (agrégats recalculés) plutôt que
// de la perdre en totalité. Une séance orpheline sans point exploitable (ou une
// séance muscu orpheline — la muscu se reprend via son brouillon) est purgée.
//
// À appeler une fois au démarrage à froid, avant qu'une nouvelle séance puisse
// commencer (aucune séance en cours ne peut donc être balayée par erreur).

import {
  deleteSession,
  finalizeSession,
  getTrackPoints,
  listInProgressSessions,
} from '@/lib/db';
import { aggregateFromPoints } from '@/lib/session-aggregate';

export type RecoveryOutcome = {
  /** Sorties vélo orphelines finalisées à partir de leurs points. */
  recovered: number;
  /** Séances orphelines purgées (vides, ou muscu non finalisables ici). */
  purged: number;
};

/**
 * Récupère les séances orphelines. Best-effort : avale ses erreurs (le sauvetage
 * ne doit jamais empêcher le démarrage de l'app).
 */
export async function recoverOrphanSessions(): Promise<RecoveryOutcome> {
  const outcome: RecoveryOutcome = { recovered: 0, purged: 0 };
  try {
    const orphans = await listInProgressSessions();
    for (const s of orphans) {
      try {
        if (s.type === 'velo') {
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
        // Vélo sans point exploitable, ou muscu (reprise via brouillon) : on purge
        // la ligne orpheline pour qu'elle ne pollue ni l'historique ni les backups.
        await deleteSession(s.id);
        outcome.purged++;
      } catch {
        // Une séance récalcitrante ne doit pas bloquer le sauvetage des autres.
      }
    }
  } catch {
    // Base indisponible au démarrage : on réessaiera au prochain lancement.
  }
  return outcome;
}
