package ovh.battistella.elan.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ovh.battistella.elan.data.settings.BackupLast
import ovh.battistella.elan.data.settings.SettingsRepository
import javax.inject.Inject

enum class BackupStatus { Idle, Saving, Restoring }

sealed interface BackupDialog {
    data object None : BackupDialog
    data object ConfirmRestore : BackupDialog
    data class RestoreDone(val count: Int) : BackupDialog

    data class QrImported(val fields: String) : BackupDialog
    data object QrUnrecognized : BackupDialog
}

data class BackupUi(
    val loaded: Boolean = false,
    val config: BackupFormConfig = BackupFormConfig(),
    val last: BackupLast? = null,
    val status: BackupStatus = BackupStatus.Idle,
    val error: String? = null,
    val secretsMissing: Boolean = false,
    val dialog: BackupDialog = BackupDialog.None,
) {
    val ready: Boolean get() = loaded && config.complete
}

sealed interface BackupEvent {
    data class Restored(val count: Int) : BackupEvent
}

private data class BackupLocal(
    val status: BackupStatus = BackupStatus.Idle,
    val error: String? = null,
    val dialog: BackupDialog = BackupDialog.None,
    val draft: BackupPatch = BackupPatch(),
    val pending: Int = 0,
)

private fun BackupPatch.under(top: BackupPatch) = BackupPatch(
    enabled = top.enabled ?: enabled,
    endpoint = top.endpoint ?: endpoint,
    region = top.region ?: region,
    bucket = top.bucket ?: bucket,
    objectKey = top.objectKey ?: objectKey,
    accessKeyId = top.accessKeyId ?: accessKeyId,
    secretAccessKey = top.secretAccessKey ?: secretAccessKey,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupPort,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(BackupLocal())

    private val persistLock = Mutex()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    private val secretsMissing: Flow<Boolean> =
        settings.observeSetting(SettingsRepository.Keys.BACKUP_SECRETS_MISSING).map { it == "1" }

    val ui: StateFlow<BackupUi> = combine(backup.config, settings.settings, secretsMissing, local) { config, s, missing, l ->
        val effective = l.draft.applyTo(config)
        if (l.pending == 0 && !l.draft.isEmpty && effective == config) {
            local.update { cur -> if (cur.draft == l.draft && cur.pending == 0) cur.copy(draft = BackupPatch()) else cur }
        }
        BackupUi(
            loaded = true,
            config = effective,
            last = s.backupLast,
            status = l.status,
            error = l.error,
            secretsMissing = missing,
            dialog = l.dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUi())

    fun dismissDialog() = local.update { it.copy(dialog = BackupDialog.None) }

    fun update(patch: BackupPatch) {
        if (!ui.value.loaded || patch.isEmpty) return
        local.update { it.copy(draft = it.draft.under(patch), pending = it.pending + 1) }
        viewModelScope.launch {
            try {
                persistLock.withLock {
                    backup.updateConfig(patch)
                    if (patch.accessKeyId != null || patch.secretAccessKey != null) {
                        settings.deleteSetting(SettingsRepository.Keys.BACKUP_SECRETS_MISSING)
                    }
                }
            } finally {
                local.update { it.copy(pending = it.pending - 1) }
            }
        }
    }

    fun onQrScanned(text: String) {
        val patch = backup.parseQr(text)
        if (patch == null) {
            local.update { it.copy(dialog = BackupDialog.QrUnrecognized) }
            return
        }
        update(patch)
        local.update { it.copy(dialog = BackupDialog.QrImported(backup.describeQrPatch(patch))) }
    }

    fun backupNow() {
        if (local.value.status != BackupStatus.Idle) return
        local.update { it.copy(status = BackupStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                backup.runBackup()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e.message ?: BACKUP_FAILED) }
            } finally {
                local.update { it.copy(status = BackupStatus.Idle) }
            }
        }
    }

    fun requestRestore() = local.update { it.copy(dialog = BackupDialog.ConfirmRestore) }

    fun confirmRestore() {
        dismissDialog()
        restore(activateAutoBackup = false)
    }

    fun restoreNow() = restore(activateAutoBackup = true)

    private fun restore(activateAutoBackup: Boolean) {
        if (local.value.status != BackupStatus.Idle) return
        local.update { it.copy(status = BackupStatus.Restoring, error = null) }
        viewModelScope.launch {
            try {
                val count = backup.restoreBackup()
                if (activateAutoBackup) {
                    backup.updateConfig(BackupPatch(enabled = true))
                    _events.emit(BackupEvent.Restored(count))
                } else {
                    local.update { it.copy(dialog = BackupDialog.RestoreDone(count)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e.message ?: RESTORE_FAILED) }
            } finally {
                local.update { it.copy(status = BackupStatus.Idle) }
            }
        }
    }

    private companion object {
        const val BACKUP_FAILED = "Sauvegarde impossible."
        const val RESTORE_FAILED = "Restauration impossible."
    }
}
