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

    /** QR décodé : liste des champs remplis. */
    data class QrImported(val fields: String) : BackupDialog
    data object QrUnrecognized : BackupDialog
}

data class BackupUi(
    /** Configuration lue : avant, les saisies sont ignorées (`update` ignoré avant hydratation). */
    val loaded: Boolean = false,
    val config: BackupFormConfig = BackupFormConfig(),
    val last: BackupLast? = null,
    val status: BackupStatus = BackupStatus.Idle,
    val error: String? = null,
    /** Identifiants S3 non repris de l'app d'origine : inviter à les ressaisir. */
    val secretsMissing: Boolean = false,
    val dialog: BackupDialog = BackupDialog.None,
) {
    /** La config permet de contacter le serveur. */
    val ready: Boolean get() = loaded && config.complete
}

sealed interface BackupEvent {
    /** Restauration réussie depuis la feuille du premier lancement. */
    data class Restored(val count: Int) : BackupEvent
}

private data class BackupLocal(
    val status: BackupStatus = BackupStatus.Idle,
    val error: String? = null,
    val dialog: BackupDialog = BackupDialog.None,
    /**
     * Saisies pas encore reflétées par la config persistée : appliquées
     * par-dessus dans [BackupUi.config] pour que les champs texte suivent la
     * frappe sans attendre l'aller-retour Room (sinon des caractères tapés
     * pendant l'écriture sont perdus). Vidées, dans le `combine` de l'état,
     * dès que le persisté les a rejointes.
     */
    val draft: BackupPatch = BackupPatch(),
    /** Écritures en cours (le brouillon n'est pas vidé tant qu'il en reste). */
    val pending: Int = 0,
)

/** Fusion de deux patchs : les champs de [top] priment. */
private fun BackupPatch.under(top: BackupPatch) = BackupPatch(
    enabled = top.enabled ?: enabled,
    endpoint = top.endpoint ?: endpoint,
    region = top.region ?: region,
    bucket = top.bucket ?: bucket,
    objectKey = top.objectKey ?: objectKey,
    accessKeyId = top.accessKeyId ?: accessKeyId,
    secretAccessKey = top.secretAccessKey ?: secretAccessKey,
)

/**
 * Sauvegarde S3 auto-hébergée (carte Réglages + feuille de restauration du
 * premier lancement) : configuration, QR, sauvegarde immédiate, restauration
 * avec confirmation, statut de la dernière sauvegarde. Les erreurs des
 * opérations réseau sont affichées telles que le port les décrit.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupPort,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(BackupLocal())

    /** Sérialise les écritures de config : chaque patch lit-modifie-écrit la config complète. */
    private val persistLock = Mutex()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    // Pas d'accès base dans le `combine` : chaque frappe passe par `local` et
    // doit atteindre l'écran avant la frame suivante, sinon le champ texte est
    // recomposé avec l'ancienne valeur et le curseur recule.
    private val secretsMissing: Flow<Boolean> =
        settings.observeSetting(SettingsRepository.Keys.BACKUP_SECRETS_MISSING).map { it == "1" }

    val ui: StateFlow<BackupUi> = combine(backup.config, settings.settings, secretsMissing, local) { config, s, missing, l ->
        val effective = l.draft.applyTo(config)
        // Le persisté a rejoint le brouillon : on le vide ici, contre la valeur
        // exacte que ce `combine` détient — un collecteur séparé verrait une
        // autre émission et pourrait faire réapparaître un texte périmé.
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

    /** Fusion partielle persistée ; ignorée tant que la config n'est pas lue. */
    fun update(patch: BackupPatch) {
        if (!ui.value.loaded || patch.isEmpty) return
        local.update { it.copy(draft = it.draft.under(patch), pending = it.pending + 1) }
        viewModelScope.launch {
            try {
                persistLock.withLock {
                    backup.updateConfig(patch)
                    // Une clé ressaisie lève l'invitation héritée de la migration.
                    if (patch.accessKeyId != null || patch.secretAccessKey != null) {
                        settings.deleteSetting(SettingsRepository.Keys.BACKUP_SECRETS_MISSING)
                    }
                }
            } finally {
                local.update { it.copy(pending = it.pending - 1) }
            }
        }
    }

    /** QR de configuration : applique le fragment et dit quels champs ont été remplis. */
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

    /** Restauration confirmée depuis la carte Réglages : alerte avec le nombre de séances. */
    fun confirmRestore() {
        dismissDialog()
        restore(activateAutoBackup = false)
    }

    /**
     * Restauration depuis la feuille du premier lancement : rien à écraser,
     * donc pas de confirmation ; la sauvegarde automatique est activée dans la
     * foulée vers ce serveur, et [BackupEvent.Restored] est émis.
     */
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
