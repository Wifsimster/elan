package ovh.battistella.elan.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal applicatif pour les snackbars transitoires, y compris les actions
 * réversibles « Annuler ». N'importe quel ViewModel ou gestionnaire peut
 * [show] un message ; le scaffold racine collecte [messages] et les rend sur
 * un unique [androidx.compose.material3.SnackbarHost] partagé.
 */
@Singleton
class SnackbarController @Inject constructor() {

    // Un scope à durée de vie applicative pour le travail d'annulation. Le
    // scope d'un ViewModel est annulé dès que son écran est dépilé, donc un
    // « Annuler » touché juste après un retour arrière ne ferait rien ; le
    // lancer ici découple la réversion de l'écran d'origine. Il utilise le
    // dispatcher principal (comme un viewModelScope) — les appels au dépôt
    // qu'il lance basculent eux-mêmes sur IO — ce qui le rend pilotable par
    // l'ordonnanceur de test.
    private val undoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    data class Message(
        val text: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        /** Les invites d'annulation restent plus longtemps pour être atteignables. */
        val isUndo: Boolean = false,
    )

    // Un tampon pour que les émissions d'appelants non suspendants (tryEmit)
    // ne soient pas perdues quand plusieurs arrivent avant que le collecteur
    // ne reprenne.
    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _messages.tryEmit(Message(text, actionLabel, onAction))
    }

    /**
     * Affiche un message réversible dont l'[action] s'exécute dans une
     * coroutine à portée applicative, et survit donc à la sortie de l'écran
     * d'origine.
     */
    fun showUndo(text: String, actionLabel: String, action: suspend () -> Unit) {
        _messages.tryEmit(
            Message(
                text = text,
                actionLabel = actionLabel,
                onAction = { undoScope.launch { action() } },
                isUndo = true,
            ),
        )
    }
}
