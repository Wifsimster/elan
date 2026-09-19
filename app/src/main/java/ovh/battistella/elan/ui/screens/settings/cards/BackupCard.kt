package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.ErrorNotice
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseCard
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.screens.settings.BackupDialog
import ovh.battistella.elan.ui.screens.settings.BackupPatch
import ovh.battistella.elan.ui.screens.settings.BackupStatus
import ovh.battistella.elan.ui.screens.settings.BackupUi
import ovh.battistella.elan.ui.theme.ElanTheme

/** Carte Réglages : sauvegarde/restauration vers un stockage S3 auto-hébergé (opt-in). */
@Composable
fun BackupCard(
    ui: BackupUi,
    onPatch: (BackupPatch) -> Unit,
    onQrScanned: (String) -> Unit,
    onBackupNow: () -> Unit,
    onRequestRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    PulseCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.CloudUploadOutline, color = colors.accent, title = stringResource(R.string.settings_backup_title))
        CardText(stringResource(R.string.settings_backup_intro))

        SwitchRow(
            label = stringResource(R.string.settings_backup_auto),
            checked = ui.config.enabled,
            onCheckedChange = { onPatch(BackupPatch(enabled = it)) },
        )

        BackupStatusLine(ui)

        if (ui.secretsMissing) ErrorNotice(stringResource(R.string.settings_backup_secrets_missing))

        BackupConfigFields(config = ui.config, onPatch = onPatch, onQrScanned = onQrScanned)

        ui.error?.let { ErrorNotice(it) }

        PulseButton(
            title = stringResource(R.string.settings_backup_now),
            icon = MdiIcons.CloudUpload,
            loading = ui.status == BackupStatus.Saving,
            enabled = ui.ready && ui.status == BackupStatus.Idle,
            onClick = onBackupNow,
            modifier = Modifier.fillMaxWidth(),
        )
        PulseButton(
            title = stringResource(R.string.settings_backup_restore),
            icon = MdiIcons.CloudDownloadOutline,
            variant = ButtonVariant.Secondary,
            loading = ui.status == BackupStatus.Restoring,
            enabled = ui.ready && ui.status == BackupStatus.Idle,
            onClick = onRequestRestore,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Ligne d'état de la dernière sauvegarde : pastille colorée + libellé daté. */
@Composable
private fun BackupStatusLine(ui: BackupUi) {
    val colors = ElanTheme.colors
    val last = ui.last
    val (label, color) = when {
        ui.status == BackupStatus.Saving -> stringResource(R.string.settings_backup_saving) to colors.warning
        ui.status == BackupStatus.Restoring -> stringResource(R.string.settings_backup_restoring) to colors.warning
        last != null && last.ok -> stringResource(R.string.settings_backup_last, formatDateTime(last.at)) to colors.success
        last != null -> stringResource(R.string.settings_backup_failed_at, formatDateTime(last.at)) to colors.danger
        else -> stringResource(R.string.settings_backup_none) to colors.textSecondary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        CardText(label, modifier = Modifier.weight(1f))
    }
}

/**
 * Dialogues de la sauvegarde (confirmation de restauration, résultat, QR),
 * partagés par la carte Réglages et la feuille de restauration.
 */
@Composable
fun BackupDialogs(dialog: BackupDialog, onConfirmRestore: () -> Unit, onDismiss: () -> Unit) {
    val colors = ElanTheme.colors
    when (dialog) {
        BackupDialog.None -> Unit
        BackupDialog.ConfirmRestore -> ConfirmDialog(
            title = stringResource(R.string.settings_backup_restore_title),
            text = stringResource(R.string.settings_backup_restore_text),
            confirmLabel = stringResource(R.string.settings_backup_restore_confirm),
            confirmColor = colors.danger,
            onConfirm = onConfirmRestore,
            onDismiss = onDismiss,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        is BackupDialog.RestoreDone -> InfoDialog(
            title = stringResource(R.string.settings_backup_restored_title),
            text = stringResource(R.string.settings_backup_restored_text, dialog.count),
            onDismiss = onDismiss,
        )
        is BackupDialog.QrImported -> InfoDialog(
            title = stringResource(R.string.settings_backup_qr_imported_title),
            text = stringResource(R.string.settings_backup_qr_imported_text, dialog.fields),
            onDismiss = onDismiss,
        )
        BackupDialog.QrUnrecognized -> InfoDialog(
            title = stringResource(R.string.settings_backup_qr_unrecognized_title),
            text = stringResource(R.string.settings_backup_qr_unrecognized_text),
            onDismiss = onDismiss,
        )
    }
}

/** Alerte à un seul bouton « OK » (les `Alert.alert(titre, texte)` d'origine). */
@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}
