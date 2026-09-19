// Flux de trames brutes des capteurs BLE consommés par le suivi d'une sortie.
//
// Le contrôleur s'abonne aux TRAMES (pas à une valeur dédupliquée) : un palier
// stable (FC constante, cadence constante) doit continuer à alimenter la
// moyenne et l'appariement FC ↔ point GPS. Le down-sampling des paliers est
// fait côté contrôleur (`pushDownsampled`, 1 s de garde).
//
// Les implémentations réelles vivent dans `sensors/ble` et se lient via
// `@Binds` dans `BleModule` (voir `di/TrackingModule.kt`, liaisons
// optionnelles). Les flux doivent être PASSIFS : collecter `frames()` ne
// déclenche ni scan ni connexion, il ne fait qu'écouter l'appareil déjà relié.
package ovh.battistella.elan.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Une trame de ceinture cardiaque : instant de réception et bpm. */
data class HrFrame(val ts: Long, val bpm: Int)

/**
 * Une trame de capteur cadence/vitesse (CSC). Chaque champ est `null` quand la
 * trame ne le porte pas (capteur cadence seul, capteur vitesse seul).
 */
data class CscFrame(val ts: Long, val cadenceRpm: Int?, val speedKmh: Double?)

interface HeartRateSamples {
    fun frames(): Flow<HrFrame>

    /** Aucune ceinture : flux vide. */
    object None : HeartRateSamples {
        override fun frames(): Flow<HrFrame> = emptyFlow()
    }
}

interface CadenceSamples {
    fun frames(): Flow<CscFrame>

    /** Aucun capteur : flux vide. */
    object None : CadenceSamples {
        override fun frames(): Flow<CscFrame> = emptyFlow()
    }
}
