package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.settings.AutoProgressionConfig
import ovh.battistella.elan.data.settings.NotificationConfig
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.DEFAULT_WEEK_PLAN
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.makeGoal
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.domain.removeGoal
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.domain.upsertGoal
import java.time.Clock
import javax.inject.Inject

data class TargetSpec(val unit: String, val step: Int, val min: Int, val max: Int, val def: Int)

val TARGET_SPEC: Map<GoalMetric, TargetSpec> = mapOf(
    GoalMetric.SESSIONS to TargetSpec("×", 1, 1, 30, 3),
    GoalMetric.DISTANCE to TargetSpec("km", 5, 5, 1000, 100),
    GoalMetric.TONNAGE to TargetSpec("kg", 100, 100, 100_000, 5000),
)

data class GoalForm(
    val metric: GoalMetric = GoalMetric.SESSIONS,
    val activity: GoalActivity = GoalActivity.ALL,
    val period: GoalPeriod = GoalPeriod.WEEK,
    val target: Int = TARGET_SPEC.getValue(GoalMetric.SESSIONS).def,
) {
    val spec: TargetSpec get() = TARGET_SPEC.getValue(metric)
}

fun goalActivityAllowed(metric: GoalMetric, activity: GoalActivity): Boolean =
    metric != GoalMetric.DISTANCE || activity == GoalActivity.ALL || activity.asActivityType()?.let { isGpsActivity(it) } == true

fun GoalActivity.asActivityType(): ActivityType? = ActivityType.fromKey(key)

data class WeekPlanOption(val label: String, val planned: PlannedSession)

private fun outingOption(label: String, kind: String) =
    WeekPlanOption(label, PlannedSession.Outing(kind, ActivityType.fromKey(kind)!!.meta.label))

private fun muscuOption(label: String, id: TemplateId) =
    WeekPlanOption(label, PlannedSession.Muscu(templateById(id).name, id))

val WEEK_PLAN_OPTIONS: List<WeekPlanOption> = listOf(
    WeekPlanOption("Repos", PlannedSession.Repos),
    outingOption("Vélo", "velo"),
    outingOption("Course", "course"),
    outingOption("Marche", "marche"),
    muscuOption("Muscu A", TemplateId.FULLBODY_A),
    muscuOption("Muscu B", TemplateId.FULLBODY_B),
    muscuOption("Dos", TemplateId.DOS_LOMBAIRE),
    muscuOption("Cervicales", TemplateId.CERVICALES),
)

fun isOptionActive(opt: WeekPlanOption, entry: PlannedSession): Boolean = when (val p = opt.planned) {
    PlannedSession.Repos -> entry is PlannedSession.Repos
    is PlannedSession.Outing -> entry is PlannedSession.Outing && entry.kind == p.kind
    is PlannedSession.Muscu -> entry is PlannedSession.Muscu && entry.templateId == p.templateId
}

data class StravaLastImport(
    val imported: Int,
    val duplicates: Int,
    val skipped: Int,
    val errors: Int,
    val at: Long,
    val details: List<String> = emptyList(),
)

internal fun parseStravaLastImport(raw: String?): StravaLastImport? {
    if (raw.isNullOrEmpty()) return null
    return try {
        val o = JSONObject(raw)
        StravaLastImport(o.optInt("imported"), o.optInt("duplicates"), o.optInt("skipped"), o.optInt("errors"), o.optLong("at"))
    } catch (e: JSONException) {
        null
    }
}

internal fun serializeStravaLastImport(r: StravaLastImport): String = JSONObject()
    .put("imported", r.imported)
    .put("duplicates", r.duplicates)
    .put("skipped", r.skipped)
    .put("errors", r.errors)
    .put("at", r.at)
    .toString()

val STRAVA_IMPORT_MIME_TYPES: Array<String> =
    arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")

enum class ExportKind { Markdown, Json }

sealed interface SettingsDialog {
    data object None : SettingsDialog
    data object ClearSessions : SettingsDialog
    data object ResetAll : SettingsDialog
    data object ResetWeekPlan : SettingsDialog
    data object MapUrlInvalid : SettingsDialog
    data object HealthDenied : SettingsDialog
    data object HealthUnavailable : SettingsDialog
    data class StravaResult(val report: ImportReport) : SettingsDialog
    data class Info(val title: String, val text: String) : SettingsDialog
}

data class SettingsUi(
    val loaded: Boolean = false,
    val profile: Profile = Profile(),
    val goals: List<Goal> = emptyList(),
    val goalForm: GoalForm = GoalForm(),
    val weekPlan: List<PlannedSession> = DEFAULT_WEEK_PLAN,
    val autoProgression: Boolean = true,
    val notifications: NotificationConfig = NotificationConfig(),
    val notificationsError: Boolean = false,
    val privacyZoneM: Int = 0,
    val mapStyleUrl: String = "",
    val mapUrlField: String = "",
    val mapEnabled: Boolean = false,
    val mapCustom: Boolean = false,
    val healthSupported: Boolean = false,
    val healthEnabled: Boolean = false,
    val healthBusy: Boolean = false,
    val exporting: ExportKind? = null,
    val exportError: String? = null,
    val stravaImporting: Boolean = false,
    val stravaError: String? = null,
    val stravaLast: StravaLastImport? = null,
    val dialog: SettingsDialog = SettingsDialog.None,
)

private data class SettingsLocal(
    val goalForm: GoalForm = GoalForm(),
    val notificationsError: Boolean = false,
    val mapDraft: String? = null,
    val healthBusy: Boolean = false,
    val exporting: ExportKind? = null,
    val exportError: String? = null,
    val stravaImporting: Boolean = false,
    val stravaError: String? = null,
    val stravaLast: StravaLastImport? = null,
    val dialog: SettingsDialog = SettingsDialog.None,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val reminders: RemindersPort,
    private val health: HealthConnectPort,
    private val mapStyle: MapStylePort,
    private val export: ExportPort,
    private val strava: StravaImportPort,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsLocal())

    val ui: StateFlow<SettingsUi> = combine(settings.settings, mapStyle.styleUrl, health.enabled, local) { s, url, healthOn, l ->
        val mapEnabled = url.isNotBlank()
        val custom = if (url == mapStyle.openFreeMapStyleUrl) "" else url
        SettingsUi(
            loaded = true,
            profile = s.profile,
            goals = s.goals,
            goalForm = l.goalForm,
            weekPlan = s.weekPlan.takeIf { it.size == 7 } ?: DEFAULT_WEEK_PLAN,
            autoProgression = s.autoProgression.enabled,
            notifications = s.notifications,
            notificationsError = l.notificationsError,
            privacyZoneM = s.privacyZoneM.toInt(),
            mapStyleUrl = url,
            mapUrlField = l.mapDraft ?: custom,
            mapEnabled = mapEnabled,
            mapCustom = custom.isNotEmpty(),
            healthSupported = health.isSupported,
            healthEnabled = healthOn,
            healthBusy = l.healthBusy,
            exporting = l.exporting,
            exportError = l.exportError,
            stravaImporting = l.stravaImporting,
            stravaError = l.stravaError,
            stravaLast = l.stravaLast,
            dialog = l.dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    val healthPermissions: Set<String> get() = health.permissions
    fun healthContract(): ActivityResultContract<Set<String>, Set<String>> = health.permissionContract()

    init {
        viewModelScope.launch {
            val last = parseStravaLastImport(settings.getSetting(SettingsRepository.Keys.STRAVA_LAST_IMPORT))
            local.update { it.copy(stravaLast = last) }
        }
    }

    fun dismissDialog() = local.update { it.copy(dialog = SettingsDialog.None) }

    private fun info(titleRes: Int, textRes: Int) =
        local.update { it.copy(dialog = SettingsDialog.Info(context.getString(titleRes), context.getString(textRes))) }

    private fun patchProfile(transform: (Profile) -> Profile) {
        viewModelScope.launch { settings.saveProfile(transform(settings.getProfile())) }
    }

    fun setWeight(kg: Int) = patchProfile { it.copy(weightKg = kg.toDouble()) }
    fun setHeight(cm: Int) = patchProfile { it.copy(heightCm = cm.toDouble()) }
    fun setMaxHr(bpm: Int) = patchProfile { it.copy(maxHr = bpm.toDouble()) }
    fun setTrainingGoal(goal: TrainingGoal) = patchProfile { it.copy(goal = goal) }
    fun setSex(sex: Sex?) = patchProfile { it.copy(sex = sex) }

    fun setGoalMetric(metric: GoalMetric) = local.update { l ->
        val f = l.goalForm
        val activity = if (goalActivityAllowed(metric, f.activity)) f.activity else GoalActivity.ALL
        l.copy(goalForm = f.copy(metric = metric, target = TARGET_SPEC.getValue(metric).def, activity = activity))
    }

    fun setGoalActivity(activity: GoalActivity) = local.update { l ->
        if (goalActivityAllowed(l.goalForm.metric, activity)) l.copy(goalForm = l.goalForm.copy(activity = activity)) else l
    }

    fun setGoalPeriod(period: GoalPeriod) = local.update { it.copy(goalForm = it.goalForm.copy(period = period)) }

    fun setGoalTarget(target: Int) = local.update { l ->
        val spec = l.goalForm.spec
        l.copy(goalForm = l.goalForm.copy(target = target.coerceIn(spec.min, spec.max)))
    }

    fun addGoal() {
        val f = local.value.goalForm
        viewModelScope.launch {
            val goal = makeGoal(f.metric, f.period, f.target.toDouble(), if (f.metric == GoalMetric.TONNAGE) GoalActivity.ALL else f.activity)
            settings.setGoals(upsertGoal(settings.snapshot().goals, goal))
        }
    }

    fun removeGoal(id: String) {
        viewModelScope.launch { settings.setGoals(removeGoal(settings.snapshot().goals, id)) }
    }

    fun setPlanDay(dayIndex: Int, planned: PlannedSession) {
        if (dayIndex !in 0..6) return
        viewModelScope.launch {
            val current = settings.snapshot().weekPlan.takeIf { it.size == 7 } ?: DEFAULT_WEEK_PLAN
            val next = current.toMutableList().also { it[dayIndex] = planned }
            settings.setCustomWeekPlan(next)
            reminders.apply()
        }
    }

    fun requestResetWeekPlan() = local.update { it.copy(dialog = SettingsDialog.ResetWeekPlan) }

    fun confirmResetWeekPlan() {
        dismissDialog()
        viewModelScope.launch {
            settings.setCustomWeekPlan(null)
            reminders.apply()
        }
    }

    fun setAutoProgression(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoProgression(AutoProgressionConfig(enabled)) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        local.update { it.copy(notificationsError = false) }
        viewModelScope.launch {
            settings.setNotifications(settings.snapshot().notifications.copy(enabled = enabled))
            reminders.apply()
        }
    }

    fun notificationsDenied() = local.update { it.copy(notificationsError = true) }

    fun setNotificationHour(hour: Int) {
        viewModelScope.launch {
            val next = settings.snapshot().notifications.copy(hour = hour.coerceIn(0, 23))
            settings.setNotifications(next)
            if (next.enabled) reminders.apply()
        }
    }

    fun requestClearSessions() = local.update { it.copy(dialog = SettingsDialog.ClearSessions) }
    fun requestResetAll() = local.update { it.copy(dialog = SettingsDialog.ResetAll) }

    fun confirmClearSessions() {
        dismissDialog()
        viewModelScope.launch {
            try {
                sessions.clearAllData()
                info(R.string.settings_data_cleared_title, R.string.settings_data_cleared_text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                info(R.string.settings_error_title, R.string.settings_data_clear_failed)
            }
        }
    }

    fun confirmResetAll() {
        dismissDialog()
        viewModelScope.launch {
            try {
                sessions.clearAllDataIncludingSettings()
                info(R.string.settings_data_reset_title, R.string.settings_data_reset_text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                info(R.string.settings_error_title, R.string.settings_data_reset_failed)
            }
        }
    }

    fun setPrivacyZone(meters: Int) {
        viewModelScope.launch { settings.setPrivacyZoneM(meters.toDouble()) }
    }

    fun setMapEnabled(on: Boolean) {
        viewModelScope.launch {
            val current = mapStyle.styleUrl.first()
            val custom = if (current == mapStyle.openFreeMapStyleUrl) "" else current
            persistMapUrl(if (on) custom.ifEmpty { mapStyle.openFreeMapStyleUrl } else "")
        }
    }

    fun setCustomMapUrl(text: String) {
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> {
                local.update { it.copy(mapDraft = null) }
                viewModelScope.launch {
                    val enabled = mapStyle.styleUrl.first().isNotBlank()
                    persistMapUrl(if (enabled) mapStyle.openFreeMapStyleUrl else "")
                }
            }
            mapStyle.isValidMapStyleUrl(trimmed) -> {
                local.update { it.copy(mapDraft = text) }
                persistMapUrl(trimmed)
            }
            "https://".startsWith(trimmed, ignoreCase = true) -> local.update { it.copy(mapDraft = text) }
            else -> local.update { it.copy(dialog = SettingsDialog.MapUrlInvalid) }
        }
    }

    private fun persistMapUrl(url: String) {
        viewModelScope.launch {
            mapStyle.setStyleUrl(url)
            local.update { l -> if (l.mapDraft?.trim() == url || url.isEmpty()) l.copy(mapDraft = null) else l }
        }
    }

    fun disableHealthConnect() {
        viewModelScope.launch { health.disable() }
    }

    fun healthRequestStarted() = local.update { it.copy(healthBusy = true) }

    fun onHealthPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            val outcome = try {
                health.onPermissionResult(granted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HealthConnectOutcome.Unavailable
            }
            local.update {
                it.copy(
                    healthBusy = false,
                    dialog = when (outcome) {
                        HealthConnectOutcome.Granted -> it.dialog
                        HealthConnectOutcome.Denied -> SettingsDialog.HealthDenied
                        HealthConnectOutcome.Unavailable -> SettingsDialog.HealthUnavailable
                    },
                )
            }
        }
    }

    fun healthUnavailable() = local.update { it.copy(healthBusy = false, dialog = SettingsDialog.HealthUnavailable) }

    fun exportMarkdown() = runExport(ExportKind.Markdown)
    fun exportJson() = runExport(ExportKind.Json)

    private fun runExport(kind: ExportKind) {
        if (local.value.exporting != null) return
        local.update { it.copy(exporting = kind, exportError = null) }
        viewModelScope.launch {
            try {
                val file = when (kind) {
                    ExportKind.Markdown -> export.exportMarkdown(context)
                    ExportKind.Json -> export.exportJson(context)
                }
                val mime = if (kind == ExportKind.Markdown) "text/markdown" else "application/json"
                export.share(context, file, mime, context.getString(R.string.settings_export_title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(exportError = e.message ?: context.getString(R.string.settings_export_failed)) }
            } finally {
                local.update { it.copy(exporting = null) }
            }
        }
    }

    fun importStrava(uris: List<Uri>) {
        if (uris.isEmpty() || local.value.stravaImporting) return
        local.update { it.copy(stravaImporting = true, stravaError = null) }
        viewModelScope.launch {
            try {
                val report = strava.importUris(uris)
                val last = StravaLastImport(report.imported, report.duplicates, report.skipped, report.errors, clock.millis(), report.details)
                settings.setSetting(SettingsRepository.Keys.STRAVA_LAST_IMPORT, serializeStravaLastImport(last))
                local.update { it.copy(stravaLast = last, dialog = SettingsDialog.StravaResult(report)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(stravaError = e.message ?: context.getString(R.string.settings_strava_failed)) }
            } finally {
                local.update { it.copy(stravaImporting = false) }
            }
        }
    }
}
