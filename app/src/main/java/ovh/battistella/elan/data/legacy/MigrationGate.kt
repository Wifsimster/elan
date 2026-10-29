package ovh.battistella.elan.data.legacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MigrationState {
    data object Pending : MigrationState

    data class Running(val copied: Long, val total: Long) : MigrationState

    data object Ready : MigrationState

    data class Failed(val reason: String, val canRetry: Boolean) : MigrationState
}

@Singleton
class MigrationGate @Inject constructor(
    private val importer: LegacyDatabaseImporter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<MigrationState>(MigrationState.Pending)
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun start() = runAttempts(MAX_AUTO_ATTEMPTS)

    fun retry(): Job = scope.launch { runAttempts(1) }

    fun skip(): Job = scope.launch {
        mutex.withLock {
            _state.value = MigrationState.Running(0, 0)
            try {
                importer.markSkipped()
                _state.value = MigrationState.Ready
            } catch (e: Exception) {
                _state.value = MigrationState.Failed(e.message ?: e.javaClass.simpleName, canRetry = true)
            }
        }
    }

    private suspend fun runAttempts(maxAttempts: Int) = mutex.withLock {
        if (_state.value == MigrationState.Ready) return@withLock
        var attempts = 0
        while (true) {
            attempts++
            _state.value = MigrationState.Running(0, 0)
            val outcome = importer.runIfNeeded { copied, total ->
                _state.value = MigrationState.Running(copied, total)
            }
            when (outcome) {
                ImportOutcome.NothingToDo, is ImportOutcome.Imported -> {
                    _state.value = MigrationState.Ready
                    return@withLock
                }
                is ImportOutcome.Failed -> {
                    if (outcome.retriable && attempts < maxAttempts) continue
                    _state.value = MigrationState.Failed(outcome.reason, canRetry = outcome.retriable)
                    return@withLock
                }
            }
        }
    }

    companion object {
        const val MAX_AUTO_ATTEMPTS = 3
    }
}
