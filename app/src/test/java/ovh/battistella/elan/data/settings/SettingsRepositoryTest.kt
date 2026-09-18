package ovh.battistella.elan.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import ovh.battistella.elan.domain.DEFAULT_WEEK_PLAN
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.testing.TestSupport

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var db: ElanDatabase
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        settings = TestSupport.repositories(db).settings
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun current() = settings.settings.first()

    @Test
    fun `sans aucune ligne les défauts sont ceux d'un profil neuf`() = runTest {
        assertEquals(ElanSettings(), current())
        val s = current()
        assertEquals(Profile(70.0, 175.0, 190.0, TrainingGoal.HYPERTROPHIE, null), s.profile)
        assertEquals(2105.0, s.cscWheelMm, 0.0)
        assertEquals(BackupConfig(false, "", "us-east-1", "", "elan-backup.json"), s.backupConfig)
        assertEquals(NotificationConfig(false, 12), s.notifications)
        assertEquals(DEFAULT_WEEK_PLAN, s.weekPlan)
        assertTrue(s.autoProgression.enabled)
        assertEquals(AutoProgressionState("", emptyList(), false), s.autoProgressionState)
        assertFalse(s.onboardingDone)
    }

    @Test
    fun `des valeurs corrompues retombent sur les défauts sans lever`() = runTest {
        for (key in listOf(
            Keys.PROFILE, Keys.HR_DEVICE, Keys.CSC_DEVICES, Keys.BACKUP_S3, Keys.BACKUP_LAST,
            Keys.NOTIFICATIONS, Keys.WEEK_PLAN, Keys.GOALS, Keys.AUTO_PROGRESSION,
            Keys.AUTO_PROGRESSION_STATE, Keys.MUSCU_DRAFT,
        )) settings.setSetting(key, "{not json")
        settings.setSetting(Keys.CSC_WHEEL_MM, "abc")
        settings.setSetting(Keys.PRIVACY_ZONE_M, "-5")
        settings.setSetting(Keys.REST_SECONDS, "zéro")
        settings.setSetting(Keys.MAP_STYLE_URL, "http://insecure.example/style.json")
        settings.setSetting(Keys.HEALTH_CONNECT, "true")

        // Tout au défaut, sauf `onboarding_done` qui n'est pas posé.
        assertEquals(ElanSettings(), current())
    }

    @Test
    fun `lecture tolérante champ par champ`() = runTest {
        // Profil partiel : les champs absents gardent leur défaut (spread de l'original).
        settings.setSetting(Keys.PROFILE, """{"weightKg":78,"maxHr":188,"goal":"bogus","sex":"f"}""")
        // Heure hors plage → 12 ; enabled non booléen → false.
        settings.setSetting(Keys.NOTIFICATIONS, """{"enabled":"yes","hour":25}""")
        // Entrées de capteurs mal formées ignorées.
        settings.setSetting(Keys.CSC_DEVICES, """[{"id":"a","name":"Roue"},{"name":"sans id"},42]""")
        settings.setSetting(Keys.CSC_WHEEL_MM, " 2096 ")
        settings.setSetting(Keys.BACKUP_S3, """{"enabled":true,"endpoint":"https://s3.example","bucket":"b","region":"","objectKey":""}""")
        settings.setSetting(Keys.BACKUP_LAST, """{"at":1700000000000,"ok":false,"error":"boom"}""")
        settings.setSetting(Keys.MAP_STYLE_URL, "HTTPS://tiles.example/style.json")
        settings.setSetting(Keys.HEALTH_CONNECT, "1")
        settings.setSetting(Keys.PRIVACY_ZONE_M, "200")
        settings.setSetting(Keys.REST_SECONDS, "5")
        settings.setSetting(Keys.ONBOARDING_DONE, "1")
        settings.setSetting(Keys.AUTO_PROGRESSION, """{"enabled":false}""")
        settings.setSetting(
            Keys.AUTO_PROGRESSION_STATE,
            """{"week":"2026-W38","dismissed":"non","changes":[
                {"exercise":"Goblet squat","kind":"load","from":20,"to":22.5,"direction":"up"},
                {"exercise":"Planche","kind":"time","from":30,"to":35,"direction":"none"},
                "junk"]}""",
        )

        val s = current()
        assertEquals(Profile(78.0, 175.0, 188.0, TrainingGoal.HYPERTROPHIE, Sex.F), s.profile)
        assertEquals(NotificationConfig(false, 12), s.notifications)
        assertEquals(listOf(BleDevice("a", "Roue")), s.cscDevices)
        assertEquals(2096.0, s.cscWheelMm, 0.0)
        assertEquals(BackupConfig(true, "https://s3.example", "us-east-1", "b", "elan-backup.json"), s.backupConfig)
        assertEquals(BackupLast(1_700_000_000_000L, false, "boom"), s.backupLast)
        assertEquals("HTTPS://tiles.example/style.json", s.mapStyleUrl)
        assertTrue(s.healthConnect)
        assertEquals(200.0, s.privacyZoneM, 0.0)
        assertEquals(15, s.restSeconds) // borné à 15..600
        assertTrue(s.onboardingDone)
        assertFalse(s.autoProgression.enabled)
        assertEquals(
            AutoProgressionState(
                week = "2026-W38",
                changes = listOf(ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP)),
                dismissed = false,
            ),
            s.autoProgressionState,
        )
    }

    @Test
    fun `chaque réglage fait l'aller-retour setX puis lecture`() = runTest {
        val profile = Profile(82.5, 180.0, 185.0, TrainingGoal.FORCE, Sex.H)
        val plan = listOf(
            PlannedSession.Repos,
            PlannedSession.Muscu("Dos", TemplateId.DOS_LOMBAIRE),
            PlannedSession.Outing("course", "Footing"),
            PlannedSession.Repos,
            PlannedSession.Muscu("Full-body B", TemplateId.FULLBODY_B),
            PlannedSession.Outing("marche", "Balade"),
            PlannedSession.Repos,
        )
        val goals = listOf(Goal("g1", GoalMetric.DISTANCE, GoalPeriod.MONTH, 100.0, GoalActivity.VELO))
        val draft = MuscuDraft(
            startedAt = 1_700_000_000_000L,
            elapsedSec = 125.0,
            exercises = JSONArray().put(JSONObject().put("name", "Squat")),
            hrSamples = listOf(HrSample(1_700_000_001_000L, 130.0)),
        )
        val state = AutoProgressionState(
            "2026-W38",
            listOf(ProgressionChange("Planche", ProgressKind.TIME, 30.0, 35.0, Direction.UP)),
            dismissed = true,
        )

        settings.setProfile(profile)
        settings.setHrDevice(BleDevice("hr-1", "Ceinture"))
        settings.setCscDevices(listOf(BleDevice("c1", "Roue"), BleDevice("c2", "Pédalier")))
        settings.setCscWheelMm(2136.0)
        settings.setBackupConfig(BackupConfig(true, "https://s3.example/base", "eu-west-1", "elan", "backup.json"))
        settings.setBackupLast(BackupLast(42L, true))
        settings.setMapStyleUrl("https://tiles.openfreemap.org/styles/liberty")
        settings.setHealthConnect(true)
        settings.setNotifications(NotificationConfig(true, 7))
        settings.setCustomWeekPlan(plan)
        settings.setGoals(goals)
        settings.setAutoProgression(AutoProgressionConfig(false))
        settings.setAutoProgressionState(state)
        settings.setMuscuDraft(draft)
        settings.setPrivacyZoneM(499.6)
        settings.setRestSeconds(90)
        settings.setOnboardingDone(true)

        val s = current()
        assertEquals(profile, s.profile)
        assertEquals(BleDevice("hr-1", "Ceinture"), s.hrDevice)
        assertEquals(listOf(BleDevice("c1", "Roue"), BleDevice("c2", "Pédalier")), s.cscDevices)
        assertEquals(2136.0, s.cscWheelMm, 0.0)
        assertEquals(BackupConfig(true, "https://s3.example/base", "eu-west-1", "elan", "backup.json"), s.backupConfig)
        assertEquals(BackupLast(42L, true, null), s.backupLast)
        assertEquals("https://tiles.openfreemap.org/styles/liberty", s.mapStyleUrl)
        assertTrue(s.healthConnect)
        assertEquals(NotificationConfig(true, 7), s.notifications)
        assertEquals(plan, s.customWeekPlan)
        assertEquals(plan, s.weekPlan)
        assertEquals(goals, s.goals)
        assertFalse(s.autoProgression.enabled)
        assertEquals(state, s.autoProgressionState)
        assertEquals(draft.startedAt, s.muscuDraft!!.startedAt)
        assertEquals(draft.hrSamples, s.muscuDraft!!.hrSamples)
        assertEquals("Squat", s.muscuDraft!!.exercises.getJSONObject(0).getString("name"))
        assertEquals(500.0, s.privacyZoneM, 0.0)
        assertEquals(90, s.restSeconds)
        assertTrue(s.onboardingDone)

        // Formes stockées = celles de l'app d'origine (échangeables par sauvegarde).
        assertEquals("""{"weightKg":82.5,"heightCm":180,"maxHr":185,"goal":"force","sex":"h"}""", settings.getSetting(Keys.PROFILE))
        assertEquals("2136", settings.getSetting(Keys.CSC_WHEEL_MM))
        assertEquals("500", settings.getSetting(Keys.PRIVACY_ZONE_M))
        assertEquals("1", settings.getSetting(Keys.HEALTH_CONNECT))
        assertEquals("1", settings.getSetting(Keys.ONBOARDING_DONE))
        assertEquals("""[{"kind":"repos"},{"kind":"muscu","label":"Dos","templateId":"dos-lombaire"},{"kind":"course","label":"Footing"},{"kind":"repos"},{"kind":"muscu","label":"Full-body B","templateId":"fullbody-b"},{"kind":"marche","label":"Balade"},{"kind":"repos"}]""", settings.getSetting(Keys.WEEK_PLAN))

        // Les « null » effacent la clé.
        settings.setHrDevice(null)
        settings.setCustomWeekPlan(null)
        settings.setMuscuDraft(null)
        settings.setOnboardingDone(false)
        settings.setHealthConnect(false)
        val cleared = current()
        assertNull(cleared.hrDevice)
        assertNull(cleared.customWeekPlan)
        assertEquals(DEFAULT_WEEK_PLAN, cleared.weekPlan)
        assertNull(cleared.muscuDraft)
        assertFalse(cleared.onboardingDone)
        assertFalse(cleared.healthConnect)
        assertNull(settings.getSetting(Keys.HR_DEVICE))
        assertEquals("", settings.getSetting(Keys.HEALTH_CONNECT))
    }

    @Test
    fun `un planning invalide retombe sur le défaut`() = runTest {
        // Six entrées seulement.
        settings.setSetting(Keys.WEEK_PLAN, """[{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"}]""")
        assertNull(current().customWeekPlan)
        assertEquals(DEFAULT_WEEK_PLAN, current().weekPlan)
        // Template inconnu.
        settings.setSetting(Keys.WEEK_PLAN, """[{"kind":"muscu","label":"X","templateId":"nope"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"}]""")
        assertEquals(DEFAULT_WEEK_PLAN, current().weekPlan)
        // Valide : restauré d'une sauvegarde, n'importe quel template connu passe.
        settings.setSetting(Keys.WEEK_PLAN, """[{"kind":"muscu","label":"X","templateId":"cervicales"},{"kind":"velo","label":"V"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"},{"kind":"repos"}]""")
        assertEquals(PlannedSession.Muscu("X", TemplateId.CERVICALES), current().weekPlan[0])
        assertEquals(PlannedSession.Outing("velo", "V"), current().weekPlan[1])
    }

    @Test
    fun `snapshot et restore reproduisent les réglages sans toucher aux clés de l'appareil`() = runTest {
        settings.setProfile(Profile(weightKg = 66.0))
        settings.setGoals(listOf(Goal("g", GoalMetric.SESSIONS, GoalPeriod.WEEK, 3.0, GoalActivity.ALL)))
        settings.setRestSeconds(120)
        settings.setOnboardingDone(true)
        settings.setBackupConfig(BackupConfig(enabled = true, endpoint = "https://a.example", bucket = "a"))
        settings.setMapStyleUrl("https://a.example/style.json")
        val snap = settings.snapshot()

        // Un autre appareil, avec sa propre config S3 et sa propre carte.
        val other = TestSupport.inMemoryDb()
        try {
            val target = TestSupport.repositories(other).settings
            target.setBackupConfig(BackupConfig(enabled = false, endpoint = "https://b.example", bucket = "b"))
            target.setMapStyleUrl("")
            target.setRestSeconds(45)
            target.restore(snap)

            val restored = target.settings.first()
            assertEquals(snap.profile, restored.profile)
            assertEquals(snap.goals, restored.goals)
            assertEquals(120, restored.restSeconds)
            assertTrue(restored.onboardingDone)
            assertEquals(BackupConfig(enabled = false, endpoint = "https://b.example", bucket = "b"), restored.backupConfig)
            assertEquals("", restored.mapStyleUrl)

            // Un instantané aux défauts remet aussi les clés optionnelles à zéro.
            target.restore(ElanSettings())
            assertNull(target.settings.first().restSeconds)
            assertEquals(ElanSettings().copy(backupConfig = restored.backupConfig), target.settings.first())
        } finally {
            other.close()
        }
    }

    @Test
    fun `le flux se met à jour après une écriture`() = runTest {
        assertEquals(Profile(), current().profile)
        settings.setProfile(Profile(weightKg = 90.0))
        assertEquals(90.0, current().profile.weightKg, 0.0)
        settings.deleteSetting(Keys.PROFILE)
        assertEquals(Profile(), current().profile)
    }

    @Test
    fun `les secrets S3 hérités d'un ancien JSON sont détectés sans être exposés`() {
        assertNull(SettingsJson.legacyBackupSecrets("""{"endpoint":"https://a","bucket":"b"}"""))
        assertEquals("AK" to "SK", SettingsJson.legacyBackupSecrets("""{"accessKeyId":"AK","secretAccessKey":"SK"}"""))
        assertEquals("" to "SK", SettingsJson.legacyBackupSecrets("""{"secretAccessKey":"SK"}"""))
        assertEquals(BackupConfig(), SettingsJson.parseBackupConfig("""{"accessKeyId":"AK","secretAccessKey":"SK"}"""))
        assertFalse(SettingsJson.serializeBackupConfig(BackupConfig(bucket = "b")).contains("accessKeyId"))
    }
}
