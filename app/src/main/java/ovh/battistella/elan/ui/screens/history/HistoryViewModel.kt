package ovh.battistella.elan.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.battistella.elan.data.repository.ListSessionsOptions
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Session
import java.time.Clock
import javax.inject.Inject

/** Plage de dates de l'historique (`7|30|90|all` dans l'app d'origine). */
enum class HistoryRange(val days: Int?) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    ALL(null),
}

data class HistoryUi(
    /** `null` = toutes les activités. */
    val typeFilter: ActivityType? = null,
    val range: HistoryRange = HistoryRange.ALL,
    /** Texte tapé (avant debounce). */
    val searchInput: String = "",
    val sessions: List<Session> = emptyList(),
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    /** Au moins une première page reçue : les états vides peuvent s'afficher. */
    val loaded: Boolean = false,
) {
    /** Un filtre masque des résultats : l'état vide ne propose pas l'import. */
    val filtered: Boolean get() = typeFilter != null || range != HistoryRange.ALL || searchInput.trim().isNotEmpty()
}

/** Délai entre la dernière frappe et la requête. */
const val SEARCH_DEBOUNCE_MS = 200L

/** Taille de page de la liste (`PAGE_SIZE` d'origine). */
const val PAGE_SIZE = 50

/** Critères effectifs d'une requête (après debounce de la recherche). */
private data class Query(val type: ActivityType?, val range: HistoryRange, val search: String)

@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(HistoryUi())
    val ui: StateFlow<HistoryUi> = _ui.asStateFlow()

    private val typeFilter = MutableStateFlow<ActivityType?>(null)
    private val range = MutableStateFlow(HistoryRange.ALL)
    private val searchInput = MutableStateFlow("")
    private val refreshTick = MutableStateFlow(0)

    // Compteur de « run » : tout résultat d'un run obsolète (filtre changé
    // entre temps) est ignoré, ce qui évite les courses de réponses.
    private var run = 0
    private var current = Query(null, HistoryRange.ALL, "")

    init {
        // La saisie vide s'applique tout de suite (premier chargement, effacement) ;
        // une frappe attend 200 ms de silence.
        val search = searchInput
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .map { it.trim() }
            .distinctUntilChanged()
        viewModelScope.launch {
            combine(typeFilter, range, search, refreshTick) { t, r, s, _ -> Query(t, r, s) }
                .collectLatest { q ->
                    current = q
                    loadFirstPage(q)
                }
        }
    }

    fun setTypeFilter(type: ActivityType?) {
        typeFilter.value = type
        _ui.update { it.copy(typeFilter = type) }
    }

    fun setRange(value: HistoryRange) {
        range.value = value
        _ui.update { it.copy(range = value) }
    }

    fun setSearchInput(text: String) {
        searchInput.value = text
        _ui.update { it.copy(searchInput = text) }
    }

    /** Au retour sur l'onglet : reflète une séance fraîchement enregistrée. */
    fun refresh() = refreshTick.update { it + 1 }

    /** Page suivante quand on approche du bas de la liste. */
    fun loadMore() {
        val state = _ui.value
        if (state.loading || !state.hasMore) return
        val myRun = ++run
        val q = current
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val rows = runCatching { sessions.listSessions(options(q, offset = state.sessions.size)) }
                .getOrDefault(emptyList())
            if (run != myRun) return@launch
            _ui.update { it.copy(sessions = it.sessions + rows, hasMore = rows.size == PAGE_SIZE, loading = false) }
        }
    }

    private suspend fun loadFirstPage(q: Query) {
        val myRun = ++run
        _ui.update { it.copy(loading = true) }
        val rows = runCatching { sessions.listSessions(options(q, offset = 0)) }.getOrDefault(emptyList())
        if (run != myRun) return
        _ui.update { it.copy(sessions = rows, hasMore = rows.size == PAGE_SIZE, loading = false, loaded = true) }
    }

    private fun options(q: Query, offset: Int) = ListSessionsOptions(
        limit = PAGE_SIZE,
        offset = offset,
        type = q.type,
        search = q.search.ifEmpty { null },
        fromMs = q.range.days?.let { clock.millis() - it * DAY_MS },
    )

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
