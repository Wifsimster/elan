package ovh.battistella.elan.ui.screens.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.ExercisePoint
import ovh.battistella.elan.domain.ProgressionAdvice
import ovh.battistella.elan.domain.catalogByName
import ovh.battistella.elan.domain.epley1RM
import ovh.battistella.elan.domain.exerciseByName
import ovh.battistella.elan.domain.exerciseHowTo
import ovh.battistella.elan.domain.suggestProgression
import ovh.battistella.elan.ui.components.BarPoint
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Fiche d'exécution d'un exercice connu (programme, sinon catalogue). */
data class ExerciseGuide(val imageKey: String?, val icon: String?, val muscles: List<String>, val howTo: String)

/** Résout la fiche depuis les programmes, puis le catalogue, sinon `null` (saisie libre). */
fun resolveGuide(name: String): ExerciseGuide? {
    exerciseByName(name)?.let { return ExerciseGuide(it.imageKey, it.icon, it.muscles, it.howTo) }
    catalogByName(name)?.let { return ExerciseGuide(it.imageKey, it.icon, it.muscles, exerciseHowTo(it.id) ?: "") }
    return null
}

data class ExerciseUi(
    val name: String = "",
    val guide: ExerciseGuide? = null,
    /** `null` = chargement en cours. */
    val points: List<ExercisePoint>? = null,
) {
    private val list: List<ExercisePoint> get() = points.orEmpty()
    val best: Double get() = list.maxOfOrNull { it.maxWeightKg } ?: 0.0
    val first: Double get() = list.firstOrNull()?.maxWeightKg ?: 0.0
    val last: Double get() = list.lastOrNull()?.maxWeightKg ?: 0.0
    val delta: Double get() = last - first

    /** Force estimée (1RM Epley) : 0 pour les exercices sans charge. */
    val best1rm: Double get() = list.maxOfOrNull { epley1RM(it.maxWeightKg, it.topReps) } ?: 0.0
    val last1rm: Double get() = list.lastOrNull()?.let { epley1RM(it.maxWeightKg, it.topReps) } ?: 0.0

    /** Charge max des 10 dernières séances, libellés `J/M`. */
    val bars: List<BarPoint>
        get() = list.takeLast(10).map { p ->
            val d = Instant.ofEpochMilli(p.startedAt).atZone(ZoneId.systemDefault())
            BarPoint("${d.dayOfMonth}/${d.monthValue}", p.maxWeightKg)
        }

    val ratings: List<Difficulty?> get() = list.map { it.difficulty }
    val hasRating: Boolean get() = ratings.any { it != null }
    val advice: ProgressionAdvice get() = suggestProgression(ratings)
    val lastRating: Difficulty? get() = ratings.lastOrNull { it != null }
}

/** Fiche d'un exercice (port de `exercise/[name].tsx`) : historique, records, 1RM, conseil. */
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
) : ViewModel() {

    val name: String = savedStateHandle.get<String>("name").orEmpty()

    private val _ui = MutableStateFlow(ExerciseUi(name = name, guide = resolveGuide(name)))
    val ui: StateFlow<ExerciseUi> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (name.isEmpty()) {
            _ui.value = _ui.value.copy(points = emptyList())
            return
        }
        viewModelScope.launch {
            val points = sessions.exerciseHistory(name)
            _ui.value = _ui.value.copy(points = points)
        }
    }
}
