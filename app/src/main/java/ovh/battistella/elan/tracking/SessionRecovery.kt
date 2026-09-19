// Sauvetage au démarrage des séances laissées « en cours » (endedAt NULL) par
// un crash / kill mémoire en pleine séance (port de session-recovery.ts).
//
// La sortie GPS écrit ses points en base au fil de l'eau (flush incrémental)
// et crée sa séance dès le `begin()`. Si l'app est tuée avant « Terminer », la
// séance reste orpheline : au prochain lancement on la finalise à partir de
// ses points survivants (agrégats recalculés) plutôt que de la perdre. Une
// séance orpheline sans point exploitable — ou une séance muscu orpheline, qui
// se reprend via son brouillon — est purgée.
//
// À appeler une fois au démarrage à froid, avant qu'une nouvelle séance puisse
// commencer (aucune séance en cours ne peut donc être balayée par erreur).
package ovh.battistella.elan.tracking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.repository.TrackPointInput
import ovh.battistella.elan.domain.aggregateFromPoints
import ovh.battistella.elan.domain.isGpsActivity
import javax.inject.Inject
import javax.inject.Singleton

data class RecoveryOutcome(
    /** Sorties GPS orphelines finalisées à partir de leurs points. */
    val recovered: Int,
    /** Séances orphelines purgées (vides, ou muscu non finalisables ici). */
    val purged: Int,
)

@Singleton
class SessionRecovery @Inject constructor(
    private val sessions: SessionRepository,
    private val controller: TrackingController,
) {
    /**
     * Récupère les séances orphelines. Best-effort : avale ses erreurs, une
     * séance récalcitrante ne bloque pas le sauvetage des autres.
     */
    suspend fun recoverOrphans(): RecoveryOutcome {
        var recovered = 0
        var purged = 0
        try {
            for (s in sessions.listInProgressSessions()) {
                try {
                    if (isGpsActivity(s.type)) {
                        val points = sessions.getTrackPoints(s.id)
                        val agg = aggregateFromPoints(points)
                        if (agg != null) {
                            val patch = SessionUpdate {
                                endedAt = agg.endedAt
                                durationSec = agg.durationSec
                                movingTimeSec = agg.movingTimeSec
                                distanceM = agg.distanceM
                                avgSpeedKmh = agg.avgSpeedKmh
                                maxSpeedKmh = agg.maxSpeedKmh
                                elevationGainM = agg.elevationGainM
                                avgHr = agg.avgHr
                                maxHr = agg.maxHr
                                avgCadence = agg.avgCadence
                                maxCadence = agg.maxCadence
                                notes = RECOVERED_NOTE
                            }
                            val inputs = points.map {
                                TrackPointInput(it.ts, it.lat, it.lon, it.altitude, it.speedKmh, it.hr, it.cadence)
                            }
                            sessions.finalizeSession(s.id, patch, inputs)
                            recovered++
                            continue
                        }
                    }
                    // GPS sans point exploitable, ou muscu (reprise via brouillon) :
                    // on purge pour ne polluer ni l'historique ni les sauvegardes.
                    sessions.deleteSession(s.id)
                    purged++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "séance orpheline ${s.id} ignorée", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sauvetage des séances orphelines impossible", e)
        }
        return RecoveryOutcome(recovered, purged)
    }

    /**
     * Coupe un `TrackingService` orphelin : après un crash, un service
     * ressuscité sans sortie en cours afficherait « Sortie en cours » sans
     * qu'aucun écran puisse l'arrêter. No-op si une sortie est réellement en
     * cours ou si le service ne tourne pas.
     */
    fun reconcileOrphanService(context: Context) {
        if (controller.state.value.isLive) return
        try {
            context.stopService(TrackingService.intent(context))
        } catch (e: Exception) {
            Log.w(TAG, "arrêt du service orphelin impossible", e)
        }
    }

    companion object {
        private const val TAG = "SessionRecovery"
        const val RECOVERED_NOTE = "Sortie récupérée après interruption"
    }
}
