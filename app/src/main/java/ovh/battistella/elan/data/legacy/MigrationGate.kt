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

/** État de la reprise des anciennes données, observé par l'écran d'accueil. */
sealed interface MigrationState {
    /** Pas encore examiné (quelques millisecondes au lancement). */
    data object Pending : MigrationState

    /** Copie en cours ; `total` = 0 tant que l'étendue n'est pas connue. */
    data class Running(val copied: Long, val total: Long) : MigrationState

    /** L'app peut démarrer (importé, rien à importer, ou abandon assumé). */
    data object Ready : MigrationState

    /** Échec ; [canRetry] faux quand rejouer est vain (base plus récente, corrompue). */
    data class Failed(val reason: String, val canRetry: Boolean) : MigrationState
}

/**
 * Barrière de démarrage : tant que l'import de l'ancienne base n'est pas
 * réglé, l'interface n'est pas montée (aucun écran ne doit lire une base à
 * moitié remplie). [start] enchaîne jusqu'à [MAX_AUTO_ATTEMPTS] tentatives
 * pour un échec rejouable ; au-delà, l'utilisateur tranche : [retry] ou
 * [skip] (continuer sans ses anciennes données).
 *
 * [retry] et [skip] tournent sur la portée applicative, pas celle de
 * l'activité : une rotation ou un passage en arrière-plan n'annule pas une
 * transaction de copie en cours.
 */
@Singleton
class MigrationGate @Inject constructor(
    private val importer: LegacyDatabaseImporter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<MigrationState>(MigrationState.Pending)
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    /** Une seule passe à la fois (le démarrage et un « Réessayer » impatient). */
    private val mutex = Mutex()

    /** Au démarrage : importe si nécessaire, avec les tentatives automatiques. */
    suspend fun start() = runAttempts(MAX_AUTO_ATTEMPTS)

    /** Nouvelle tentative demandée par l'utilisateur (une seule). */
    fun retry(): Job = scope.launch { runAttempts(1) }

    /** Continuer sans les anciennes données : marqué fait, base héritée mise de côté. */
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
