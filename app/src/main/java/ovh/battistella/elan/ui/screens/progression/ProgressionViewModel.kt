package ovh.battistella.elan.ui.screens.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.repository.ExerciseSummary
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.isoWeekKey
import java.time.Clock
import javax.inject.Inject

data class ProgressionUi(
    /** `null` = chargement en cours (évite un flash d'état vide). */
    val items: List<ExerciseSummary>? = null,
    /** Changements de la progression auto pour la semaine en cours. */
    val changes: List<ProgressionChange> = emptyList(),
)

/** Écran Progression (port de `progression.tsx`) : index des exercices et revue de la semaine. */
@HiltViewModel
class ProgressionViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProgressionUi())
    val ui: StateFlow<ProgressionUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    /** Au focus : `listMuscuExercises()` + état de la progression auto. */
    fun refresh() {
        viewModelScope.launch {
            val items = sessions.listMuscuExercises()
            val state = settings.snapshot().autoProgressionState
            val changes = if (state.week == isoWeekKey(clock.millis())) state.changes else emptyList()
            _ui.value = ProgressionUi(items = items, changes = changes)
        }
    }
}
