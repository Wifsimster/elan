package ovh.battistella.elan.data.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ovh.battistella.elan.data.local.SettingEntity
import ovh.battistella.elan.data.local.SettingsDao
import ovh.battistella.elan.domain.DEFAULT_WEEK_PLAN
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.serializeGoals
import javax.inject.Inject
import javax.inject.Singleton

data class ElanSettings(
    val profile: Profile = Profile(),
    val hrDevice: BleDevice? = null,
    val cscDevices: List<BleDevice> = emptyList(),
    val cscWheelMm: Double = SettingsJson.DEFAULT_WHEEL_MM,
    val backupConfig: BackupConfig = BackupConfig(),
    val backupLast: BackupLast? = null,
    val mapStyleUrl: String = "",
    val healthConnect: Boolean = false,
    val notifications: NotificationConfig = NotificationConfig(),
    val customWeekPlan: List<PlannedSession>? = null,
    val goals: List<Goal> = emptyList(),
    val autoProgression: AutoProgressionConfig = AutoProgressionConfig(),
    val autoProgressionState: AutoProgressionState = AutoProgressionState(),
    val muscuDraft: MuscuDraft? = null,
    val privacyZoneM: Double = 0.0,
    val restSeconds: Int? = null,
    val onboardingDone: Boolean = false,
) {
    val weekPlan: List<PlannedSession> get() = customWeekPlan ?: DEFAULT_WEEK_PLAN
}

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val io: CoroutineDispatcher,
) {
    object Keys {
        const val PROFILE = "profile"
        const val HR_DEVICE = "hr_device"
        const val CSC_DEVICES = "csc_devices"
        const val CSC_WHEEL_MM = "csc_wheel_mm"
        const val BACKUP_S3 = "backup_s3"
        const val BACKUP_LAST = "backup_last"
        const val MAP_STYLE_URL = "map_style_url"
        const val HEALTH_CONNECT = "health_connect"
        const val NOTIFICATIONS = "notifications"
        const val WEEK_PLAN = "week_plan"
        const val GOALS = "goals"
        const val AUTO_PROGRESSION = "auto_progression"
        const val AUTO_PROGRESSION_STATE = "auto_progression_state"
        const val MUSCU_DRAFT = "muscu_draft"
        const val PRIVACY_ZONE_M = "privacy_zone_m"
        const val REST_SECONDS = "rest_seconds"
        const val ONBOARDING_DONE = "onboarding_done"

        const val BACKUP_SECRETS_MISSING = "backup_secrets_missing"

        const val LEGACY_IMPORT_DONE = "legacy_import_done"

        const val LEGACY_IMPORT_AT = "legacy_import_at"

        const val LEGACY_IMPORT_COUNTS = "legacy_import_counts"

        const val LEGACY_IMPORT_ATTEMPTS = "legacy_import_attempts"

        const val LEGACY_IMPORT_ERROR = "legacy_import_error"

        const val LEGACY_IMPORT_SKIPPED = "legacy_import_skipped"

        const val LEGACY_CLEANUP_DONE = "legacy_cleanup_done"

        const val STRAVA_LAST_IMPORT = "strava_last_import"

        val LEGACY_KEYS: Set<String> = setOf(
            LEGACY_IMPORT_DONE,
            LEGACY_IMPORT_AT,
            LEGACY_IMPORT_COUNTS,
            LEGACY_IMPORT_ATTEMPTS,
            LEGACY_IMPORT_ERROR,
            LEGACY_IMPORT_SKIPPED,
            LEGACY_CLEANUP_DONE,
        )

        val BACKUP_EXCLUDED: Set<String> =
            setOf(BACKUP_S3, BACKUP_LAST, MAP_STYLE_URL, BACKUP_SECRETS_MISSING, STRAVA_LAST_IMPORT) + LEGACY_KEYS

        val RESET_KEEP: Set<String> = setOf(BACKUP_S3, BACKUP_LAST, BACKUP_SECRETS_MISSING) + LEGACY_KEYS
    }

    val settings: Flow<ElanSettings> = settingsDao.observeAll()
        .map { rows -> SettingsJson.decode(rows.associate { it.key to it.value }) }
        .distinctUntilChanged()

    suspend fun getSetting(key: String): String? = withContext(io) { settingsDao.get(key) }

    fun observeSetting(key: String): Flow<String?> = settingsDao.observeAll()
        .map { rows -> rows.firstOrNull { it.key == key }?.value }
        .distinctUntilChanged()

    suspend fun setSetting(key: String, value: String) =
        withContext(io) { settingsDao.upsert(SettingEntity(key, value)) }

    suspend fun deleteSetting(key: String) = withContext(io) { settingsDao.delete(key) }

    suspend fun getProfile(): Profile = SettingsJson.parseProfile(getSetting(Keys.PROFILE))

    suspend fun saveProfile(profile: Profile) =
        setSetting(Keys.PROFILE, SettingsJson.serializeProfile(profile))

    suspend fun setProfile(value: Profile) = saveProfile(value)

    suspend fun setHrDevice(value: BleDevice?) =
        setOrDelete(Keys.HR_DEVICE, value?.let(SettingsJson::serializeDevice))

    suspend fun setCscDevices(value: List<BleDevice>) =
        setSetting(Keys.CSC_DEVICES, SettingsJson.serializeDevices(value))

    suspend fun setCscWheelMm(value: Double) =
        setSetting(Keys.CSC_WHEEL_MM, SettingsJson.serializeNumber(value))

    suspend fun setBackupConfig(value: BackupConfig) =
        setSetting(Keys.BACKUP_S3, SettingsJson.serializeBackupConfig(value))

    suspend fun setBackupLast(value: BackupLast) =
        setSetting(Keys.BACKUP_LAST, SettingsJson.serializeBackupLast(value))

    suspend fun setMapStyleUrl(value: String) = setSetting(Keys.MAP_STYLE_URL, value)

    suspend fun setHealthConnect(value: Boolean) =
        setSetting(Keys.HEALTH_CONNECT, if (value) "1" else "")

    suspend fun setNotifications(value: NotificationConfig) =
        setSetting(Keys.NOTIFICATIONS, SettingsJson.serializeNotifications(value))

    suspend fun setCustomWeekPlan(value: List<PlannedSession>?) =
        setOrDelete(Keys.WEEK_PLAN, value?.let(SettingsJson::serializeWeekPlan))

    suspend fun setGoals(value: List<Goal>) = setSetting(Keys.GOALS, serializeGoals(value))

    suspend fun setAutoProgression(value: AutoProgressionConfig) =
        setSetting(Keys.AUTO_PROGRESSION, SettingsJson.serializeAutoProgression(value))

    suspend fun setAutoProgressionState(value: AutoProgressionState) =
        setSetting(Keys.AUTO_PROGRESSION_STATE, SettingsJson.serializeAutoProgressionState(value))

    suspend fun setMuscuDraft(value: MuscuDraft?) =
        setOrDelete(Keys.MUSCU_DRAFT, value?.let(SettingsJson::serializeDraft))

    suspend fun setPrivacyZoneM(meters: Double) =
        setSetting(Keys.PRIVACY_ZONE_M, SettingsJson.serializePrivacyZoneM(meters))

    suspend fun setRestSeconds(value: Int) = setSetting(Keys.REST_SECONDS, value.toString())

    suspend fun setOnboardingDone(value: Boolean) =
        setOrDelete(Keys.ONBOARDING_DONE, if (value) "1" else null)

    suspend fun snapshot(): ElanSettings = settings.first()

    suspend fun restore(s: ElanSettings) {
        setProfile(s.profile)
        setHrDevice(s.hrDevice)
        setCscDevices(s.cscDevices)
        setCscWheelMm(s.cscWheelMm)
        setHealthConnect(s.healthConnect)
        setNotifications(s.notifications)
        setCustomWeekPlan(s.customWeekPlan)
        setGoals(s.goals)
        setAutoProgression(s.autoProgression)
        setAutoProgressionState(s.autoProgressionState)
        setMuscuDraft(s.muscuDraft)
        setPrivacyZoneM(s.privacyZoneM)
        if (s.restSeconds != null) setRestSeconds(s.restSeconds) else deleteSetting(Keys.REST_SECONDS)
        setOnboardingDone(s.onboardingDone)
    }

    private suspend fun setOrDelete(key: String, value: String?) =
        if (value == null) deleteSetting(key) else setSetting(key, value)
}
