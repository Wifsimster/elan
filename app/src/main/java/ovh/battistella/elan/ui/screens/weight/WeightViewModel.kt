package ovh.battistella.elan.ui.screens.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.repository.BodyWeightRepository
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.BodyMeasurement
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

private const val DAY_MS = 86_400_000L

/** 76.4 -> « 76,4 » ; 76 -> « 76 ». */
fun fmtWeight(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v).replace('.', ',')

/** Variation signée : +0,4 / −1,2 / 0. */
fun fmtWeightDelta(v: Double): String = when {
    v > 0 -> "+${fmtWeight(v)}"
    v < 0 -> "−${fmtWeight(abs(v))}"
    else -> "0"
}

/**
 * Saisie utilisateur (« 76,4 », « 76.4 », « 76 ») -> poids en kg arrondi au
 * dixième, ou `null` si invalide / hors plage plausible (20–300 kg).
 */
fun parseWeightInput(text: String): Double? {
    val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!v.isFinite()) return null
    val rounded = (v * 10).roundToLong() / 10.0
    if (rounded < 20 || rounded > 300) return null
    return rounded
}

/** Pesée la plus récente antérieure ou égale à [beforeMs] (liste triée de la plus récente à la plus ancienne). */
internal fun findBefore(items: List<BodyMeasurement>, beforeMs: Long): BodyMeasurement? =
    items.firstOrNull { it.measuredAt <= beforeMs }

data class WeightUi(
    /** `null` = chargement (évite un flash d'état vide). */
    val items: List<BodyMeasurement>? = null,
    val input: String = "",
    val error: Boolean = false,
    /** Pesée dont la suppression attend confirmation. */
    val pendingDelete: BodyMeasurement? = null,
) {
    val latest: BodyMeasurement? get() = items?.firstOrNull()
    val oldest: BodyMeasurement? get() = items?.lastOrNull()

    /** Pesée de référence d'il y a 30 jours (ou la plus récente avant). */
    val monthAgo: BodyMeasurement? get() = latest?.let { l -> findBefore(items.orEmpty(), l.measuredAt - 30 * DAY_MS) }

    /** Delta sur 30 jours, `null` sans référence. */
    val deltaMonth: Double? get() = latest?.let { l -> monthAgo?.let { l.weightKg - it.weightKg } }

    /** Delta depuis la première pesée. */
    val deltaSinceStart: Double? get() = latest?.let { l -> oldest?.let { l.weightKg - it.weightKg } }
}

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val bodyWeight: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(WeightUi())
    val ui: StateFlow<WeightUi> = _ui.asStateFlow()

    init {
        load()
    }

    /** Relit le journal et pré-remplit la saisie avec le dernier poids connu (journal, sinon profil). */
    fun load() {
        viewModelScope.launch {
            val list = bodyWeight.listBodyMeasurements()
            val prefill = list.firstOrNull()?.weightKg ?: settings.getProfile().weightKg
            _ui.update { it.copy(items = list, input = fmtWeight(prefill)) }
        }
    }

    fun setInput(text: String) = _ui.update { it.copy(input = text.take(6)) }

    /** « Enregistrer » : valide, journalise (le profil suit) et recharge. */
    fun save() {
        val weightKg = parseWeightInput(_ui.value.input)
        if (weightKg == null) {
            _ui.update { it.copy(error = true) }
            return
        }
        _ui.update { it.copy(error = false) }
        viewModelScope.launch {
            bodyWeight.logBodyWeight(weightKg, clock.millis())
            load()
        }
    }

    fun requestDelete(measurement: BodyMeasurement) = _ui.update { it.copy(pendingDelete = measurement) }

    fun dismissDelete() = _ui.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val m = _ui.value.pendingDelete ?: return
        _ui.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            bodyWeight.deleteBodyMeasurement(m.id)
            load()
        }
    }
}
