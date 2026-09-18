// Chronomètre du temps actif d'une séance, avec pause (miroir de use-stopwatch.ts).
//
// `elapsedSec` est un StateFlow rafraîchi toutes les 250 ms tant que le chrono
// tourne ; `getElapsedSec()` lit la valeur LIVE depuis l'horloge, à utiliser au
// moment de l'enregistrement pour que la durée corresponde à l'instant du
// « Terminer », pas au dernier tick.
package ovh.battistella.elan.tracking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.math.floor
import kotlin.math.max

class Stopwatch(
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Secondes figées avant la pause en cours. */
    private var accumulatedSec = 0.0
    private var startedAt: Long? = null
    private var ticker: Job? = null

    /** Temps écoulé live (secondes entières), lu depuis l'horloge. */
    fun getElapsedSec(): Int {
        val since = startedAt?.let { (clock.millis() - it) / 1000.0 } ?: 0.0
        return floor(accumulatedSec + since).toInt()
    }

    fun start() {
        if (startedAt != null) return
        startedAt = clock.millis()
        _running.value = true
        publish()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                publish()
            }
        }
    }

    fun pause() {
        val started = startedAt
        if (started != null) {
            accumulatedSec += (clock.millis() - started) / 1000.0
            startedAt = null
        }
        stopTicker()
        publish()
    }

    fun reset() {
        accumulatedSec = 0.0
        startedAt = null
        stopTicker()
        _elapsedSec.value = 0
    }

    /**
     * Pré-charge un temps écoulé (reprise d'une séance en pause) sans démarrer :
     * le prochain `start()` enchaîne à partir de cette base.
     */
    fun seed(sec: Int) {
        accumulatedSec = max(0, sec).toDouble()
        startedAt = null
        stopTicker()
        _elapsedSec.value = floor(accumulatedSec).toInt()
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        _running.value = false
    }

    private fun publish() {
        _elapsedSec.value = getElapsedSec()
    }

    companion object {
        const val TICK_MS = 250L
    }
}
