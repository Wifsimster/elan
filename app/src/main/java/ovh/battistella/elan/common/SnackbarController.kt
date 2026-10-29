package ovh.battistella.elan.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnackbarController @Inject constructor() {

    private val undoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    data class Message(
        val text: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
        val isUndo: Boolean = false,
    )

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _messages.tryEmit(Message(text, actionLabel, onAction))
    }

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
