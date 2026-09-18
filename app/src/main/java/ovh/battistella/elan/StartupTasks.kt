package ovh.battistella.elan

import android.util.Log
import kotlinx.coroutines.CancellationException
import ovh.battistella.elan.data.legacy.LegacyDatabaseImporter
import ovh.battistella.elan.data.legacy.MigrationGate
import ovh.battistella.elan.data.legacy.MigrationState
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Effets exécutés une fois par processus, au lancement (voir
 * `docs/port-spec/02-interface.md` §1 « Effets au démarrage »), dans l'ordre :
 *
 * 1. reprise de l'ancienne base (`MigrationGate`) — tout le reste attend
 *    qu'elle soit réglée, car les étapes suivantes lisent la base ;
 * 2. récupération des séances orphelines (`recoverOrphanSessions`) ;
 * 3. effacement de la notification « séance en cours » ;
 * 4. rappels hebdomadaires (`applyNotifications`) et progression du programme
 *    (`runWeeklyProgressionIfDue`).
 *
 * Chaque étape est best-effort : un échec n'empêche pas les suivantes ni le
 * démarrage de l'interface.
 */
@Singleton
class StartupTasks @Inject constructor(
    private val migrationGate: MigrationGate,
    private val legacyImporter: LegacyDatabaseImporter,
    private val clock: Clock,
) {
    suspend fun run() {
        migrationGate.start()
        if (migrationGate.state.value != MigrationState.Ready) return

        bestEffort { legacyImporter.cleanupIfDue(clock.millis()) }

        // TODO(port-spec 02-interface §1) : recoverOrphanSessions() + reconcileOrphanGpsTask()
        //   — séances `endedAt IS NULL` à finaliser depuis leurs points, service GPS orphelin.
        // TODO(port-spec 02-interface §1) : clearLiveSessionNotification() — notification
        //   « séance en cours » (id `live-session`) laissée par un processus tué.
        // TODO(port-spec 02-interface §1) : applyNotifications() + runWeeklyProgressionIfDue(now)
        //   — rappels hebdomadaires (canal `routine`) et progression automatique.
    }

    /** Une étape ratée est journalisée, jamais propagée ; l'annulation, elle, passe. */
    private inline fun bestEffort(step: () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "tâche de démarrage ignorée", e)
        }
    }

    private companion object {
        const val TAG = "StartupTasks"
    }
}
