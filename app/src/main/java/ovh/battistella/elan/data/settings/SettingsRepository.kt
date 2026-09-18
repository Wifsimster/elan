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

/**
 * Instantané de tous les réglages utilisateur, décodé depuis la table
 * `settings`. Les défauts sont ceux d'un profil neuf.
 */
data class ElanSettings(
    val profile: Profile = Profile(),
    /** Ceinture cardio mémorisée (reconnexion automatique). */
    val hrDevice: BleDevice? = null,
    /** Capteurs cadence/vitesse mémorisés (0 à 2). */
    val cscDevices: List<BleDevice> = emptyList(),
    /** Circonférence de roue en mm (700×25c par défaut). */
    val cscWheelMm: Double = SettingsJson.DEFAULT_WHEEL_MM,
    val backupConfig: BackupConfig = BackupConfig(),
    val backupLast: BackupLast? = null,
    /** URL HTTPS du style MapLibre, ou '' (carte hors ligne). */
    val mapStyleUrl: String = "",
    val healthConnect: Boolean = false,
    val notifications: NotificationConfig = NotificationConfig(),
    /** Planning personnalisé valide, ou `null` → [DEFAULT_WEEK_PLAN]. */
    val customWeekPlan: List<PlannedSession>? = null,
    val goals: List<Goal> = emptyList(),
    val autoProgression: AutoProgressionConfig = AutoProgressionConfig(),
    val autoProgressionState: AutoProgressionState = AutoProgressionState(),
    /** Séance muscu en pause, reprenable. */
    val muscuDraft: MuscuDraft? = null,
    /** Rayon de la zone de confidentialité (m), 0 = désactivée. */
    val privacyZoneM: Double = 0.0,
    /** Repos préféré entre séries (15..600 s), `null` = repos conseillé pour l'objectif. */
    val restSeconds: Int? = null,
    val onboardingDone: Boolean = false,
) {
    /** Planning effectif : personnalisé s'il est valide, sinon le défaut. */
    val weekPlan: List<PlannedSession> get() = customWeekPlan ?: DEFAULT_WEEK_PLAN
}

/**
 * Source de vérité des réglages, dans la table `settings` (clé/valeur texte)
 * de la base — pas de DataStore : les réglages voyagent dans la sauvegarde S3
 * avec les séances, comme dans l'app d'origine. Expose le bundle en [Flow] pour
 * que toute couche réagisse aux changements, des setters typés, et l'accès brut
 * clé/valeur pour les écrans qui lisent une clé isolée.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val io: CoroutineDispatcher,
) {
    /** Clés de la table `settings` — celles de l'app d'origine, à l'identique. */
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

        /**
         * Clés exclues des sauvegardes (secrets, propres à l'appareil) — ni
         * exportées ni restaurées. `map_style_url` y figure car c'est un puits
         * réseau sensible : une sauvegarde falsifiée pourrait y injecter un hôte
         * qui recevrait la zone du parcours + l'IP au prochain affichage de carte.
         */
        val BACKUP_EXCLUDED: Set<String> = setOf(BACKUP_S3, BACKUP_LAST, MAP_STYLE_URL)

        /**
         * Clés préservées par une réinitialisation complète : la config de
         * sauvegarde locale, pour ne pas la casser. `map_style_url` n'en fait
         * pas partie : exclue des sauvegardes, mais bien effacée par un reset.
         */
        val RESET_KEEP: Set<String> = setOf(BACKUP_S3, BACKUP_LAST)
    }

    val settings: Flow<ElanSettings> = settingsDao.observeAll()
        .map { rows -> SettingsJson.decode(rows.associate { it.key to it.value }) }
        .distinctUntilChanged()

    // ---- accès brut clé/valeur ------------------------------------------

    suspend fun getSetting(key: String): String? = withContext(io) { settingsDao.get(key) }

    suspend fun setSetting(key: String, value: String) =
        withContext(io) { settingsDao.upsert(SettingEntity(key, value)) }

    suspend fun deleteSetting(key: String) = withContext(io) { settingsDao.delete(key) }

    // ---- profil ----------------------------------------------------------

    suspend fun getProfile(): Profile = SettingsJson.parseProfile(getSetting(Keys.PROFILE))

    suspend fun saveProfile(profile: Profile) =
        setSetting(Keys.PROFILE, SettingsJson.serializeProfile(profile))

    // ---- setters typés ---------------------------------------------------

    suspend fun setProfile(value: Profile) = saveProfile(value)

    /** `null` = oublier la ceinture. */
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

    /** `null` = effacer le plan personnalisé (retour au défaut). */
    suspend fun setCustomWeekPlan(value: List<PlannedSession>?) =
        setOrDelete(Keys.WEEK_PLAN, value?.let(SettingsJson::serializeWeekPlan))

    suspend fun setGoals(value: List<Goal>) = setSetting(Keys.GOALS, serializeGoals(value))

    suspend fun setAutoProgression(value: AutoProgressionConfig) =
        setSetting(Keys.AUTO_PROGRESSION, SettingsJson.serializeAutoProgression(value))

    suspend fun setAutoProgressionState(value: AutoProgressionState) =
        setSetting(Keys.AUTO_PROGRESSION_STATE, SettingsJson.serializeAutoProgressionState(value))

    /** `null` = brouillon effacé (séance terminée ou abandonnée). */
    suspend fun setMuscuDraft(value: MuscuDraft?) =
        setOrDelete(Keys.MUSCU_DRAFT, value?.let(SettingsJson::serializeDraft))

    suspend fun setPrivacyZoneM(meters: Double) =
        setSetting(Keys.PRIVACY_ZONE_M, SettingsJson.serializePrivacyZoneM(meters))

    suspend fun setRestSeconds(value: Int) = setSetting(Keys.REST_SECONDS, value.toString())

    suspend fun setOnboardingDone(value: Boolean) =
        setOrDelete(Keys.ONBOARDING_DONE, if (value) "1" else null)

    // ---- sauvegarde ------------------------------------------------------

    /** Instantané ponctuel des réglages courants. */
    suspend fun snapshot(): ElanSettings = settings.first()

    /**
     * Réécrit chaque réglage depuis un instantané. Les clés exclues des
     * sauvegardes (S3, dernier statut, fond de carte) ne sont PAS touchées :
     * elles appartiennent à l'appareil.
     */
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
