// Codecs JSON des réglages (org.json, comme le domaine). Lecture TOLÉRANTE :
// une valeur absente, corrompue ou mal typée retombe sur le défaut, jamais
// sur une exception — ce sont les mêmes repli que `src/lib/*.ts`, un réglage
// illisible ne doit pas bloquer l'app. Écriture au format exact de l'app
// d'origine (mêmes clés, mêmes formes), pour que les sauvegardes S3 restent
// échangeables entre les deux versions.
package ovh.battistella.elan.data.settings

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.parseGoals
import ovh.battistella.elan.domain.parseMuscuDraft
import ovh.battistella.elan.domain.progressionChangeFrom
import ovh.battistella.elan.domain.serializeMuscuDraft
import ovh.battistella.elan.domain.toJsString
import ovh.battistella.elan.domain.weekPlanFrom
import kotlin.math.floor

object SettingsJson {

    // ---- helpers ---------------------------------------------------------

    /** `JSON.parse` tolérant : `null` si vide, invalide, ou pas un objet. */
    private fun parseObject(raw: String?): JSONObject? {
        if (raw.isNullOrEmpty()) return null
        return try {
            JSONObject(raw)
        } catch (e: JSONException) {
            null
        }
    }

    private fun parseArray(raw: String?): JSONArray? {
        if (raw.isNullOrEmpty()) return null
        return try {
            JSONArray(raw)
        } catch (e: JSONException) {
            null
        }
    }

    private fun JSONObject.number(key: String): Double? = (opt(key) as? Number)?.toDouble()
    private fun JSONObject.string(key: String): String? = opt(key) as? String
    private fun JSONObject.boolean(key: String): Boolean? = opt(key) as? Boolean

    /**
     * `Number(str)` de JavaScript sur une chaîne : vide → 0, non numérique →
     * NaN (Kotlin accepte aussi « Infinity »/« NaN », que JS produit de même).
     */
    fun jsNumber(raw: String): Double {
        val t = raw.trim()
        if (t.isEmpty()) return 0.0
        return t.toDoubleOrNull() ?: Double.NaN
    }

    /** Valeur org.json → structures Kotlin (Map/List/scalaires, `NULL` → null). */
    fun toKotlin(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> LinkedHashMap<String, Any?>().also { m ->
            for (k in value.keys()) m[k] = toKotlin(value.opt(k))
        }
        is JSONArray -> List(value.length()) { toKotlin(value.opt(it)) }
        else -> value
    }

    // ---- profile ---------------------------------------------------------

    /** `{...DEFAULT_PROFILE, ...JSON.parse(raw)}` ; défaut si absent ou illisible. */
    fun parseProfile(raw: String?): Profile {
        val d = Profile()
        val o = parseObject(raw) ?: return d
        return Profile(
            weightKg = o.number("weightKg") ?: d.weightKg,
            heightCm = o.number("heightCm") ?: d.heightCm,
            maxHr = o.number("maxHr") ?: d.maxHr,
            goal = TrainingGoal.fromKey(o.string("goal")) ?: d.goal,
            sex = Sex.fromKey(o.string("sex")),
        )
    }

    fun serializeProfile(p: Profile): String = JSONObject()
        .put("weightKg", p.weightKg)
        .put("heightCm", p.heightCm)
        .put("maxHr", p.maxHr)
        .put("goal", p.goal.key)
        .put("sex", p.sex?.key ?: JSONObject.NULL)
        .toString()

    // ---- capteurs BLE ----------------------------------------------------

    private fun deviceFrom(o: JSONObject?): BleDevice? {
        val id = o?.string("id") ?: return null
        return BleDevice(id, o.string("name") ?: "")
    }

    fun parseHrDevice(raw: String?): BleDevice? = deviceFrom(parseObject(raw))

    fun serializeDevice(d: BleDevice): String = deviceJson(d).toString()

    private fun deviceJson(d: BleDevice) = JSONObject().put("id", d.id).put("name", d.name)

    fun parseCscDevices(raw: String?): List<BleDevice> {
        val arr = parseArray(raw) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { deviceFrom(arr.optJSONObject(it)) }
    }

    fun serializeDevices(devices: List<BleDevice>): String =
        JSONArray().also { arr -> devices.forEach { arr.put(deviceJson(it)) } }.toString()

    /** Circonférence de roue (mm) : texte numérique fini, sinon 2105 (700×25c). */
    fun parseWheelMm(raw: String?): Double {
        if (raw.isNullOrEmpty()) return DEFAULT_WHEEL_MM
        val mm = jsNumber(raw)
        return if (mm.isFinite()) mm else DEFAULT_WHEEL_MM
    }

    const val DEFAULT_WHEEL_MM = 2105.0

    // ---- sauvegarde S3 ---------------------------------------------------

    /**
     * Config stockée fusionnée avec les défauts ; région et nom d'objet vides
     * reviennent à leur défaut pour que l'écran montre la valeur effective.
     * Les éventuels secrets d'une config héritée sont ignorés ici (voir
     * [legacyBackupSecrets]).
     */
    fun parseBackupConfig(raw: String?): BackupConfig {
        val d = BackupConfig()
        val o = parseObject(raw) ?: return d
        return BackupConfig(
            enabled = o.boolean("enabled") ?: d.enabled,
            endpoint = o.string("endpoint") ?: d.endpoint,
            region = o.string("region")?.takeIf { it.isNotEmpty() } ?: d.region,
            bucket = o.string("bucket") ?: d.bucket,
            objectKey = o.string("objectKey")?.takeIf { it.isNotEmpty() } ?: d.objectKey,
        )
    }

    /**
     * Secrets qu'une ancienne version aurait laissés en clair dans le JSON
     * (`accessKeyId`/`secretAccessKey`), à migrer une fois vers le stockage
     * sécurisé puis à retirer du réglage. `null` s'il n'y en a pas.
     */
    fun legacyBackupSecrets(raw: String?): Pair<String, String>? {
        val o = parseObject(raw) ?: return null
        val ak = o.string("accessKeyId") ?: ""
        val sk = o.string("secretAccessKey") ?: ""
        return if (ak.isNotEmpty() || sk.isNotEmpty()) ak to sk else null
    }

    fun serializeBackupConfig(c: BackupConfig): String = JSONObject()
        .put("enabled", c.enabled)
        .put("endpoint", c.endpoint)
        .put("region", c.region)
        .put("bucket", c.bucket)
        .put("objectKey", c.objectKey)
        .toString()

    fun parseBackupLast(raw: String?): BackupLast? {
        val o = parseObject(raw) ?: return null
        val at = o.number("at") ?: return null
        val ok = o.boolean("ok") ?: return null
        return BackupLast(at.toLong(), ok, o.string("error"))
    }

    fun serializeBackupLast(b: BackupLast): String = JSONObject()
        .put("at", b.at)
        .put("ok", b.ok)
        .apply { if (b.error != null) put("error", b.error) }
        .toString()

    // ---- carte -----------------------------------------------------------

    /** Vide (carte désactivée) ou HTTPS ; le HTTP en clair est refusé. */
    fun isValidMapStyleUrl(url: String): Boolean {
        val u = url.trim()
        return u.isEmpty() || u.startsWith("https://", ignoreCase = true)
    }

    /** Re-validation à la lecture : toute valeur héritée/incorrecte → ''. */
    fun parseMapStyleUrl(raw: String?): String {
        val url = raw ?: ""
        return if (isValidMapStyleUrl(url)) url else ""
    }

    // ---- notifications ---------------------------------------------------

    fun parseNotifications(raw: String?): NotificationConfig {
        val d = NotificationConfig()
        val o = parseObject(raw) ?: return d
        val hour = o.number("hour")
        return NotificationConfig(
            enabled = o.boolean("enabled") ?: false,
            hour = if (hour != null && hour >= 0 && hour <= 23) floor(hour).toInt() else d.hour,
        )
    }

    fun serializeNotifications(c: NotificationConfig): String =
        JSONObject().put("enabled", c.enabled).put("hour", c.hour).toString()

    // ---- planning --------------------------------------------------------

    /** Plan personnalisé valide (7 entrées bien formées), sinon `null` → défaut. */
    fun parseWeekPlan(raw: String?): List<PlannedSession>? {
        val arr = parseArray(raw) ?: return null
        return weekPlanFrom(toKotlin(arr))
    }

    fun serializeWeekPlan(plan: List<PlannedSession>): String {
        val arr = JSONArray()
        for (p in plan) {
            arr.put(
                when (p) {
                    is PlannedSession.Outing -> JSONObject().put("kind", p.kind).put("label", p.label)
                    is PlannedSession.Muscu -> JSONObject()
                        .put("kind", p.kind)
                        .put("label", p.label)
                        .put("templateId", p.templateId.key)
                    PlannedSession.Repos -> JSONObject().put("kind", p.kind)
                }
            )
        }
        return arr.toString()
    }

    // ---- progression automatique -----------------------------------------

    fun parseAutoProgression(raw: String?): AutoProgressionConfig {
        val o = parseObject(raw) ?: return AutoProgressionConfig()
        return AutoProgressionConfig(enabled = o.boolean("enabled") ?: return AutoProgressionConfig())
    }

    fun serializeAutoProgression(c: AutoProgressionConfig): String =
        JSONObject().put("enabled", c.enabled).toString()

    /** `{week, changes, dismissed}` ; `changes` filtrées par `isChange`, sinon état vide. */
    fun parseAutoProgressionState(raw: String?): AutoProgressionState {
        val o = parseObject(raw) ?: return AutoProgressionState()
        val week = o.string("week") ?: return AutoProgressionState()
        val changes = (toKotlin(o.opt("changes")) as? List<*>)
            ?.mapNotNull { progressionChangeFrom(it) }
            ?: emptyList()
        return AutoProgressionState(week, changes, dismissed = o.boolean("dismissed") == true)
    }

    fun serializeAutoProgressionState(s: AutoProgressionState): String {
        val changes = JSONArray()
        for (c in s.changes) changes.put(changeJson(c))
        return JSONObject()
            .put("week", s.week)
            .put("changes", changes)
            .put("dismissed", s.dismissed)
            .toString()
    }

    private fun changeJson(c: ProgressionChange): JSONObject = JSONObject()
        .put("exercise", c.exercise)
        .put("kind", c.kind.key)
        .put("from", c.from)
        .put("to", c.to)
        .put("direction", c.direction.key)

    // ---- brouillon muscu -------------------------------------------------

    fun parseDraft(raw: String?): MuscuDraft? = parseMuscuDraft(raw)

    fun serializeDraft(d: MuscuDraft): String = serializeMuscuDraft(d)

    // ---- scalaires -------------------------------------------------------

    /** Rayon de confidentialité (m) : nombre fini > 0, sinon 0 (désactivé). */
    fun parsePrivacyZoneM(raw: String?): Double {
        if (raw == null) return 0.0
        val v = jsNumber(raw)
        return if (v.isFinite() && v > 0) v else 0.0
    }

    /** `String(Math.max(0, Math.round(m)))` : entier positif, arrondi comme JS. */
    fun serializePrivacyZoneM(meters: Double): String =
        Math.max(0L, Math.round(meters)).toString()

    /**
     * Repos préféré entre séries : nombre fini > 0 borné à 15..600 s, sinon
     * `null` (l'écran retombe alors sur le repos conseillé pour l'objectif).
     */
    fun parseRestSeconds(raw: String?): Int? {
        if (raw == null) return null
        val v = jsNumber(raw)
        if (!v.isFinite() || v <= 0) return null
        return Math.round(v.coerceIn(15.0, 600.0)).toInt()
    }

    /** `String(number)` JS : entier sans « .0 ». */
    fun serializeNumber(v: Double): String = v.toJsString()

    // ---- (dé)codage du bundle complet ------------------------------------

    /** Décode l'intégralité des réglages depuis la table `settings` (clé → valeur). */
    fun decode(values: Map<String, String>): ElanSettings = ElanSettings(
        profile = parseProfile(values[SettingsRepository.Keys.PROFILE]),
        hrDevice = parseHrDevice(values[SettingsRepository.Keys.HR_DEVICE]),
        cscDevices = parseCscDevices(values[SettingsRepository.Keys.CSC_DEVICES]),
        cscWheelMm = parseWheelMm(values[SettingsRepository.Keys.CSC_WHEEL_MM]),
        backupConfig = parseBackupConfig(values[SettingsRepository.Keys.BACKUP_S3]),
        backupLast = parseBackupLast(values[SettingsRepository.Keys.BACKUP_LAST]),
        mapStyleUrl = parseMapStyleUrl(values[SettingsRepository.Keys.MAP_STYLE_URL]),
        healthConnect = values[SettingsRepository.Keys.HEALTH_CONNECT] == "1",
        notifications = parseNotifications(values[SettingsRepository.Keys.NOTIFICATIONS]),
        customWeekPlan = parseWeekPlan(values[SettingsRepository.Keys.WEEK_PLAN]),
        goals = parseGoals(values[SettingsRepository.Keys.GOALS]),
        autoProgression = parseAutoProgression(values[SettingsRepository.Keys.AUTO_PROGRESSION]),
        autoProgressionState = parseAutoProgressionState(values[SettingsRepository.Keys.AUTO_PROGRESSION_STATE]),
        muscuDraft = parseDraft(values[SettingsRepository.Keys.MUSCU_DRAFT]),
        privacyZoneM = parsePrivacyZoneM(values[SettingsRepository.Keys.PRIVACY_ZONE_M]),
        restSeconds = parseRestSeconds(values[SettingsRepository.Keys.REST_SECONDS]),
        // Toute valeur non vide est « vraie » (l'app d'origine testait la truthiness).
        onboardingDone = !values[SettingsRepository.Keys.ONBOARDING_DONE].isNullOrEmpty(),
    )
}
