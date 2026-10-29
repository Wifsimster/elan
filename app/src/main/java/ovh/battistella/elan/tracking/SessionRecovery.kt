package ovh.battistella.elan.tracking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.repository.TrackPointInput
import ovh.battistella.elan.domain.aggregateFromPoints
import ovh.battistella.elan.domain.isGpsActivity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class RecoveryOutcome(
    val recovered: Int,
    val purged: Int,
)

@Singleton
class SessionRecovery @Inject constructor(
    private val sessions: SessionRepository,
    private val controller: TrackingController,
    clock: Clock,
) {
    private val processStartedAt = clock.millis()

    suspend fun recoverOrphans(): RecoveryOutcome {
        var recovered = 0
        var purged = 0
        try {
            for (s in sessions.listInProgressSessions()) {
                if (s.startedAt >= processStartedAt || s.id == controller.state.value.sessionId) continue
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
