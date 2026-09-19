package ovh.battistella.elan.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.screens.common.ConfirmDialog
import ovh.battistella.elan.ui.screens.settings.cards.BackupCard
import ovh.battistella.elan.ui.screens.settings.cards.BackupDialogs
import ovh.battistella.elan.ui.screens.settings.cards.CadenceSensorCard
import ovh.battistella.elan.ui.screens.settings.cards.DataCard
import ovh.battistella.elan.ui.screens.settings.cards.DataExportCard
import ovh.battistella.elan.ui.screens.settings.cards.GoalsCard
import ovh.battistella.elan.ui.screens.settings.cards.HealthConnectCard
import ovh.battistella.elan.ui.screens.settings.cards.HeartRateCard
import ovh.battistella.elan.ui.screens.settings.cards.InfoDialog
import ovh.battistella.elan.ui.screens.settings.cards.MapCard
import ovh.battistella.elan.ui.screens.settings.cards.NotificationsCard
import ovh.battistella.elan.ui.screens.settings.cards.ProfileCard
import ovh.battistella.elan.ui.screens.settings.cards.ProgressionAutoCard
import ovh.battistella.elan.ui.screens.settings.cards.StravaImportCard
import ovh.battistella.elan.ui.screens.settings.cards.WeekPlanCard
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType

/**
 * Réglages : assemblage de cartes autonomes, dans l'ordre de l'app d'origine
 * (capteurs, carte, Health Connect, profil, objectifs, planning, progression,
 * rappels, données, exports, import Strava, sauvegarde). Trois ViewModels :
 * réglages, capteurs BLE, sauvegarde S3.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
    sensorsViewModel: SensorsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    onOpenWeight: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hr by sensorsViewModel.hr.collectAsStateWithLifecycle()
    val csc by sensorsViewModel.csc.collectAsStateWithLifecycle()
    val backup by backupViewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .screenContent()
            .padding(top = 12.dp, bottom = 40.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = PulseType.title, color = colors.text)

        HeartRateCard(
            state = hr,
            onStartScan = sensorsViewModel::startHrScan,
            onStopScan = sensorsViewModel::stopHrScan,
            onConnect = sensorsViewModel::connectHr,
            onDisconnect = sensorsViewModel::disconnectHr,
        )
        CadenceSensorCard(
            state = csc,
            onStartScan = sensorsViewModel::startCscScan,
            onStopScan = sensorsViewModel::stopCscScan,
            onConnect = sensorsViewModel::connectCsc,
            onDisconnect = sensorsViewModel::disconnectCsc,
            onWheelMm = sensorsViewModel::setWheelMm,
        )
        MapCard(
            enabled = ui.mapEnabled,
            custom = ui.mapCustom,
            customUrl = ui.mapUrlField,
            onEnabledChange = viewModel::setMapEnabled,
            onCustomUrlChange = viewModel::setCustomMapUrl,
        )
        if (ui.healthSupported) {
            HealthConnectCard(
                enabled = ui.healthEnabled,
                busy = ui.healthBusy,
                permissions = viewModel.healthPermissions,
                contract = viewModel.healthContract(),
                onDisable = viewModel::disableHealthConnect,
                onRequestStarted = viewModel::healthRequestStarted,
                onPermissionResult = viewModel::onHealthPermissionResult,
                onUnavailable = viewModel::healthUnavailable,
            )
        }
        ProfileCard(
            profile = ui.profile,
            onWeight = viewModel::setWeight,
            onHeight = viewModel::setHeight,
            onMaxHr = viewModel::setMaxHr,
            onGoal = viewModel::setTrainingGoal,
            onSex = viewModel::setSex,
            onOpenWeight = onOpenWeight,
        )
        GoalsCard(
            goals = ui.goals,
            form = ui.goalForm,
            onMetric = viewModel::setGoalMetric,
            onActivity = viewModel::setGoalActivity,
            onPeriod = viewModel::setGoalPeriod,
            onTarget = viewModel::setGoalTarget,
            onAdd = viewModel::addGoal,
            onRemove = viewModel::removeGoal,
        )
        WeekPlanCard(plan = ui.weekPlan, onDayChange = viewModel::setPlanDay, onReset = viewModel::requestResetWeekPlan)
        ProgressionAutoCard(enabled = ui.autoProgression, onEnabledChange = viewModel::setAutoProgression)
        NotificationsCard(
            config = ui.notifications,
            error = ui.notificationsError,
            onEnabledChange = viewModel::setNotificationsEnabled,
            onDenied = viewModel::notificationsDenied,
            onHourChange = viewModel::setNotificationHour,
        )
        DataCard(onClearSessions = viewModel::requestClearSessions, onResetAll = viewModel::requestResetAll)
        DataExportCard(
            privacyZoneM = ui.privacyZoneM,
            exporting = ui.exporting,
            error = ui.exportError,
            onPrivacyZone = viewModel::setPrivacyZone,
            onExportMarkdown = viewModel::exportMarkdown,
            onExportJson = viewModel::exportJson,
        )
        StravaImportCard(
            importing = ui.stravaImporting,
            error = ui.stravaError,
            last = ui.stravaLast,
            onImport = viewModel::importStrava,
        )
        BackupCard(
            ui = backup,
            onPatch = backupViewModel::update,
            onQrScanned = backupViewModel::onQrScanned,
            onBackupNow = backupViewModel::backupNow,
            onRequestRestore = backupViewModel::requestRestore,
        )
    }

    SettingsDialogs(dialog = ui.dialog, viewModel = viewModel)
    BackupDialogs(dialog = backup.dialog, onConfirmRestore = backupViewModel::confirmRestore, onDismiss = backupViewModel::dismissDialog)
}

@Composable
private fun SettingsDialogs(dialog: SettingsDialog, viewModel: SettingsViewModel) {
    val colors = ElanTheme.colors
    when (dialog) {
        SettingsDialog.None -> Unit
        SettingsDialog.ClearSessions -> ConfirmDialog(
            title = stringResource(R.string.settings_data_clear_title),
            text = stringResource(R.string.settings_data_clear_text),
            confirmLabel = stringResource(R.string.settings_data_clear_confirm),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmClearSessions,
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        SettingsDialog.ResetAll -> ConfirmDialog(
            title = stringResource(R.string.settings_data_reset_dialog_title),
            text = stringResource(R.string.settings_data_reset_dialog_text),
            confirmLabel = stringResource(R.string.settings_data_reset_confirm),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmResetAll,
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        SettingsDialog.ResetWeekPlan -> ConfirmDialog(
            title = stringResource(R.string.settings_plan_reset_title),
            text = stringResource(R.string.settings_plan_reset_text),
            confirmLabel = stringResource(R.string.settings_plan_reset_confirm),
            confirmColor = colors.danger,
            onConfirm = viewModel::confirmResetWeekPlan,
            onDismiss = viewModel::dismissDialog,
            dismissLabel = stringResource(R.string.common_cancel),
        )
        SettingsDialog.MapUrlInvalid -> InfoDialog(
            title = stringResource(R.string.settings_map_invalid_title),
            text = stringResource(R.string.settings_map_invalid_text),
            onDismiss = viewModel::dismissDialog,
        )
        SettingsDialog.HealthDenied -> InfoDialog(
            title = stringResource(R.string.settings_health_denied_title),
            text = stringResource(R.string.settings_health_denied_text),
            onDismiss = viewModel::dismissDialog,
        )
        SettingsDialog.HealthUnavailable -> InfoDialog(
            title = stringResource(R.string.settings_health_unavailable_title),
            text = stringResource(R.string.settings_health_unavailable_text),
            onDismiss = viewModel::dismissDialog,
        )
        is SettingsDialog.StravaResult -> {
            val r = dialog.report
            val errors = if (r.errors > 0) stringResource(R.string.settings_strava_result_errors, r.errors) else ""
            InfoDialog(
                title = stringResource(R.string.settings_strava_result_title),
                text = stringResource(R.string.settings_strava_result_text, r.imported, r.duplicates, r.skipped) + errors,
                onDismiss = viewModel::dismissDialog,
            )
        }
        is SettingsDialog.Info -> InfoDialog(title = dialog.title, text = dialog.text, onDismiss = viewModel::dismissDialog)
    }
}
