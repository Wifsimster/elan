package ovh.battistella.elan.ui.screens.outing

import kotlinx.coroutines.flow.StateFlow
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GeoPoint
import ovh.battistella.elan.domain.GpsStatus

/**
 * Phases d'une sortie GPS, miroir de `phase` dans `sortie.tsx` plus la phase
 * `Requesting` (premier fix / permission en attente) portée par le contrôleur.
 */
enum class OutingPhase { Idle, Requesting, Active, Paused, Saving, SaveFailed }

/**
 * État observable d'une sortie, tel que l'écran le consomme. Reflète champ
 * pour champ l'`OutingState` du `TrackingController` (module `tracking/`) ;
 * l'adaptateur vers l'implémentation réelle n'a qu'à recopier les valeurs.
 *
 * Deux champs sont propres à l'interface :
 *  - [savedSessionId] : id de la séance enregistrée avec succès — l'écran
 *    navigue alors vers son détail (`router.replace(détail)` d'origine) ;
 *  - [errorMessage] : dernier échec à présenter (« Impossible de démarrer »,
 *    « Échec de l'enregistrement ») ; `null` quand tout va bien.
 */
data class OutingUi(
    val phase: OutingPhase = OutingPhase.Idle,
    val type: ActivityType = ActivityType.VELO,
    /** Séance « en cours » créée en base au démarrage (`endedAt NULL`). */
    val sessionId: Long? = null,
    /** Chrono actif en secondes (hors pauses). */
    val elapsedSec: Int = 0,
    val distanceM: Double = 0.0,
    /** Vitesse instantanée (km/h), `null` sans fix. */
    val speedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val elevationGainM: Double = 0.0,
    val pointCount: Int = 0,
    /** Précision horizontale du dernier fix (m). */
    val accuracyM: Double? = null,
    val gpsStatus: GpsStatus = GpsStatus.IDLE,
    /** Tracé courant pour la carte live. */
    val livePath: List<GeoPoint> = emptyList(),
    /** Ceinture cardio (bpm), `null` sans ceinture. */
    val bpm: Int? = null,
    /** Capteur de cadence (tr/min). */
    val cadenceRpm: Int? = null,
    /** Capteur de vitesse roue (km/h). */
    val wheelSpeedKmh: Double? = null,
    /** Calories estimées sur la vitesse MOYENNE (kcal). */
    val caloriesLive: Double = 0.0,
    val errorMessage: String? = null,
    val savedSessionId: Long? = null,
)

/**
 * Contrat entre l'écran Sortie et le suivi GPS. Le `TrackingController`
 * (singleton) l'implémente via un adaptateur ; en attendant, une liaison
 * inerte est fournie par `ScreensModule`.
 */
interface OutingPort {
    val state: StateFlow<OutingUi>

    /** Démarre le suivi : permission, création de la séance, chrono. */
    fun begin(type: ActivityType)
    fun pause()
    fun resume()

    /** Arrête le GPS et enregistre (phase `Saving`, puis `savedSessionId` ou `SaveFailed`). */
    fun finish()

    /** Rejoue l'enregistrement après un `SaveFailed` (tracé figé, idempotent). */
    fun retrySave()

    /** Abandonne : arrêt du GPS, suppression de la séance en cours, retour à `Idle`. */
    fun discard()
}
