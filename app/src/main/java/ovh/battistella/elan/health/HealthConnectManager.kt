// Export opt-in des séances vers Android Health Connect — la base de données
// santé SUR L'APPAREIL d'Android (aucun cloud, aucun compte). Désactivé par
// défaut ; l'utilisateur l'active dans Réglages, ce qui déclenche la demande de
// permissions (écriture seule). À la fin d'une séance, `export()` écrit
// l'ExerciseSession (+ distance, calories, série FC) en best-effort : toute
// erreur est avalée, la séance locale reste la source de vérité.
package ovh.battistella.elan.health

import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.tracking.HealthExport
import ovh.battistella.elan.tracking.SavedSessionData
import javax.inject.Inject
import javax.inject.Singleton

/** Issue de l'activation (port de `EnableResult`). */
enum class HealthEnableResult { GRANTED, DENIED, UNAVAILABLE }

@Singleton
class HealthConnectManager @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val settings: SettingsRepository,
) : HealthExport {

    /** Vrai si un fournisseur Health Connect peut exister sur cet appareil (carte à afficher). */
    val isSupported: Boolean
        get() = try {
            gateway.sdkStatus() != HealthSdkStatus.UNAVAILABLE
        } catch (e: Exception) {
            false
        }

    private val _permissionRequests = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)

    /**
     * Permissions à demander au système. L'écran des réglages collecte ce flux
     * et lance le contrat [permissionContract] avec l'ensemble reçu, puis
     * rapporte le résultat via [onPermissionResult] — c'est ce qui débloque
     * [enable].
     */
    val permissionRequests: SharedFlow<Set<String>> = _permissionRequests.asSharedFlow()

    private val enableLock = Mutex()
    private var pendingResult: CompletableDeferred<Set<String>>? = null

    /** Contrat système à enregistrer dans l'écran (`rememberLauncherForActivityResult`). */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = gateway.requestPermissionContract()

    /** Résultat du contrat système : l'ensemble des permissions accordées. */
    fun onPermissionResult(granted: Set<String>) {
        pendingResult?.complete(granted)
    }

    /** Opt-in mémorisé dans `settings` — false par défaut. */
    suspend fun isEnabled(): Boolean = settings.settings.map { it.healthConnect }.first()

    /**
     * Active l'export : vérifie la disponibilité de Health Connect puis demande
     * les permissions d'écriture (l'UI système ne s'affiche qu'à ce moment-là,
     * jamais avant — c'est le contrat de l'opt-in). N'enregistre l'activation
     * que si toutes les permissions sont accordées. Suspend jusqu'au retour
     * du contrat système ; un seul appel à la fois.
     */
    suspend fun enable(): HealthEnableResult = enableLock.withLock {
        try {
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return HealthEnableResult.UNAVAILABLE
            val required = HealthRecordType.WRITE_PERMISSIONS
            val granted = if (gateway.grantedPermissions().containsAll(required)) {
                required
            } else {
                val deferred = CompletableDeferred<Set<String>>()
                pendingResult = deferred
                try {
                    if (!_permissionRequests.tryEmit(required)) return HealthEnableResult.UNAVAILABLE
                    deferred.await()
                } finally {
                    pendingResult = null
                }
            }
            if (!granted.containsAll(required)) return HealthEnableResult.DENIED
            settings.setHealthConnect(true)
            HealthEnableResult.GRANTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "activation Health Connect impossible", e)
            HealthEnableResult.UNAVAILABLE
        }
    }

    suspend fun disable() = settings.setHealthConnect(false)

    /**
     * Écrit une séance terminée. Best-effort : no-op si l'opt-in est
     * désactivé, si Health Connect est indisponible ou si une permission a été
     * révoquée entre-temps (revérifiées à chaque export : l'utilisateur peut
     * les retirer depuis Health Connect sans passer par Élan). Ne lève jamais.
     */
    suspend fun exportSession(data: HealthSessionData) {
        try {
            if (!isEnabled()) return
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return
            val granted = gateway.grantedPermissions()
            if (HealthRecordType.EXERCISE_SESSION.writePermission !in granted) return

            // Un insert par enregistrement (« All records must have the same
            // type »), chacun en best-effort pour qu'un échec n'empêche pas les suivants.
            for (record in buildHealthRecords(data)) {
                if (record.type.writePermission !in granted) continue
                try {
                    gateway.insert(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "insertion ${record.type} ignorée", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "export Health Connect ignoré", e)
        }
    }

    /**
     * Supprime le miroir d'une séance enregistrée sous [type]. Appelé avant de
     * réécrire une séance dont le TYPE a changé : l'identifiant client porte le
     * type, sans cette suppression la réécriture créerait un doublon. Best-effort.
     */
    suspend fun removeSession(type: ActivityType, startedAt: Long) {
        try {
            if (!isEnabled()) return
            if (gateway.sdkStatus() != HealthSdkStatus.AVAILABLE) return
            for (recordType in HealthRecordType.entries) {
                try {
                    gateway.deleteByClientIds(recordType, listOf(healthClientRecordId(type, startedAt, recordType.suffix)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "suppression ${recordType} ignorée", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "suppression Health Connect ignorée", e)
        }
    }

    // ---- HealthExport (SessionFinalizer) ---------------------------------

    override suspend fun export(data: SavedSessionData) = exportSession(
        HealthSessionData(
            type = data.type,
            startedAt = data.startedAt,
            endedAt = data.endedAt,
            distanceM = data.distanceM,
            calories = data.calories,
            hrSamples = data.hrSamples,
        ),
    )

    override suspend fun remove(type: ActivityType, startedAt: Long) = removeSession(type, startedAt)

    companion object {
        private const val TAG = "HealthConnect"

        /** Action envoyée par Health Connect (≤ Android 13) pour afficher la justification des permissions. */
        const val ACTION_SHOW_PERMISSIONS_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"

        /** Toutes les actions qui doivent ouvrir la justification (Android 14+ passe par `VIEW_PERMISSION_USAGE`). */
        val RATIONALE_ACTIONS: Set<String> = setOf(ACTION_SHOW_PERMISSIONS_RATIONALE, "android.intent.action.VIEW_PERMISSION_USAGE")
    }
}
