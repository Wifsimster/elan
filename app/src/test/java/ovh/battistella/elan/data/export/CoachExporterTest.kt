package ovh.battistella.elan.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.formatDateShort
import ovh.battistella.elan.domain.formatDateTime
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class CoachExporterTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private lateinit var exporter: CoachExporter
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_700_500_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        exporter = CoachExporter(repos.sessions, repos.bodyWeight, repos.settings, repos.snapshot, clock)
    }

    @After
    fun tearDown() {
        db.close()
        FileShare.purgeShared(context)
    }

    private suspend fun seed() {
        repos.settings.saveProfile(Profile(weightKg = 72.5, heightCm = 178.0, maxHr = 188.0, goal = TrainingGoal.FORCE, sex = Sex.H))
        repos.bodyWeight.logBodyWeight(73.0, now - 5 * 86_400_000L)
        repos.bodyWeight.logBodyWeight(72.5, now - 86_400_000L)
        repos.settings.setGoals(listOf(Goal("g1", GoalMetric.SESSIONS, GoalPeriod.WEEK, 2.0, GoalActivity.ALL)))

        db.sessionDao().insert(
            TestSupport.session(
                type = ActivityType.VELO, startedAt = now - 3_600_000L, endedAt = now, durationSec = 3600,
                movingTimeSec = 3000, distanceM = 25_431.7, avgSpeedKmh = 30.5, elevationGainM = 210.4, calories = 612.0,
            ),
        )
        db.sessionDao().insert(
            TestSupport.session(
                type = ActivityType.COURSE, startedAt = now - 7_200_000L, endedAt = now - 3_600_000L, durationSec = 3600,
                distanceM = 10_000.0, avgSpeedKmh = 10.0, source = "strava", externalId = "strava-x",
            ),
        )
        repos.sessions.saveMuscuSession(
            null, now - 2 * 86_400_000L, ovh.battistella.elan.data.repository.SessionUpdate { durationSec = 2400; endedAt = now - 2 * 86_400_000L + 2_400_000 },
            listOf(
                TestSupport.muscuSetInput("Goblet squat", 1, 10, 20.0, Difficulty.MOYEN),
                TestSupport.muscuSetInput("Goblet squat", 2, 8, 22.5, Difficulty.MOYEN),
                TestSupport.muscuSetInput("Pompes | lestées", 1, 12, 0.0, null),
            ),
        )
    }

    @Test
    fun `num formate en français sans décimale superflue`() {
        assertEquals("3", num(3.0))
        assertEquals("12,5", num(12.5))
        assertEquals("12,3", num(12.25))
        assertEquals("0,1", num(0.06))
        assertEquals("—", num(null))
        assertEquals("1,25", num(1.25, 2))
        assertEquals("a\\|b c", cell("a|b\n\nc "))
    }

    @Test
    fun `le bilan Markdown contient toutes les sections dans l'ordre`() = runTest {
        seed()
        val md = exporter.buildMarkdown()

        val headings = Regex("^## .*$", RegexOption.MULTILINE).findAll(md).map { it.value }.toList()
        assertEquals(
            listOf(
                "## Profil", "## Poids corporel", "## Mon programme", "## Statistiques", "## Objectifs",
                "## Progression par exercice (musculation)", "## Détail des séances de musculation", "## Historique des sorties",
            ),
            headings,
        )
        assertTrue(md.startsWith("# Élan — Export pour ton coach IA\n\nGénéré le ${formatDateTime(now)}. Données issues de l'app Élan (toutes locales).\n"))

        // Profil : nombres FR, libellés.
        assertTrue(md.contains("- Poids : 72,5 kg"))
        assertTrue(md.contains("- Taille : 178 cm"))
        assertTrue(md.contains("- FC max : 188 bpm"))
        assertTrue(md.contains("- Objectif : Force"))
        assertTrue(md.contains("- Sexe : homme"))

        // Poids : du plus ancien au plus récent.
        val i73 = md.indexOf("| ${formatDateShort(now - 5 * 86_400_000L)} | 73 |")
        val i725 = md.indexOf("| ${formatDateShort(now - 86_400_000L)} | 72,5 |")
        assertTrue(i73 in 0 until i725)

        // Programme : planning + un ### par programme, exercices numérotés.
        assertTrue(md.contains("- Lundi : Vélo — Vélo 1h"))
        assertTrue(md.contains("- Mardi : Musculation — Full-body A"))
        assertTrue(md.contains("- Mercredi : Repos"))
        assertTrue(md.contains("### Full-body A ("))
        assertTrue(md.contains("1. **"))
        assertTrue(md.contains("   _Muscles :_ "))

        // Statistiques : 4 lignes de période.
        assertTrue(md.contains("| Période | Séances | Durée totale | Distance (vélo) | Calories |"))
        assertTrue(md.contains("| 7 derniers jours | 3 | "))
        assertTrue(md.contains("| Depuis le début | 3 | "))

        // Objectifs : la muscu date de la semaine passée ; 2 sorties cette semaine sur une cible de 2 → atteint.
        assertTrue(md.contains("| 2 séances / semaine | 2 | 2 | oui |"))

        // Progression + détail muscu : pipe échappé, ressenti.
        assertTrue(md.contains("### Goblet squat\n\n| Date | Charge max (kg) | Reps | Volume | Séries | Ressenti |"))
        assertTrue(md.contains("| 22,5 | 8 | 380 | 2 | Moyen |"))
        assertTrue(md.contains("### Pompes | lestées\n"))
        assertTrue(md.contains("| Goblet squat | 2 | 8 | 22,5 |"))
        assertTrue(md.contains("| Pompes \\| lestées | 1 | 12 | 0 |"))
        assertTrue(md.contains("— durée 40:00"))

        // Sorties : temps en mouvement, allure pour la course, source.
        assertTrue(md.contains("| Vélo | 50:00 | 25,4 km | 30,5 km/h | — | — | 210 m | 612 kcal | app |"))
        assertTrue(md.contains("| Course à pied | 1:00:00 | 10,0 km | 6:00 /km | — | — | — | — | strava |"))
    }

    @Test
    fun `sections vides — omises ou remplacées par une mention`() = runTest {
        val md = exporter.buildMarkdown()
        assertFalse(md.contains("## Poids corporel"))
        assertFalse(md.contains("## Objectifs"))
        assertTrue(md.contains("## Progression par exercice (musculation)\n\n_Aucune séance de musculation enregistrée._"))
        assertTrue(md.contains("## Détail des séances de musculation\n\n_Aucune séance de musculation enregistrée._"))
        assertTrue(md.contains("## Historique des sorties\n\n_Aucune sortie enregistrée._"))
        assertTrue(md.contains("- Sexe : non précisé"))
    }

    @Test
    fun `exportMarkdown et exportJson écrivent dans le cache partagé`() = runTest {
        seed()
        repos.settings.setSetting(Keys.MAP_STYLE_URL, "https://tiles")

        val md = exporter.exportMarkdown(context)
        assertEquals("suivi-sport-coach.md", md.name)
        assertEquals(FileShare.shareDir(context), md.parentFile)
        assertTrue(md.readText().startsWith("# Élan"))

        val json = exporter.exportJson(context)
        assertEquals("suivi-sport-export.json", json.name)
        val o = JSONObject(json.readText())
        assertEquals(1, o.getInt("format"))
        assertEquals("suivi-sport", o.getString("app"))
        assertEquals(now, o.getLong("exportedAt"))
        assertEquals(72.5, o.getJSONObject("profile").getDouble("weightKg"), 0.0)
        assertEquals("h", o.getJSONObject("profile").getString("sex"))
        val program = o.getJSONObject("program")
        assertEquals(4, program.getJSONArray("templates").length())
        assertEquals("fullbody-a", program.getJSONArray("templates").getJSONObject(0).getString("id"))
        assertEquals(7, program.getJSONArray("weekPlan").length())
        assertEquals("velo", program.getJSONArray("weekPlan").getJSONObject(0).getString("kind"))
        val data = o.getJSONObject("data")
        assertEquals(3, data.getJSONArray("sessions").length())
        assertEquals(3, data.getJSONArray("muscuSets").length())
        assertEquals(2, data.getJSONArray("bodyMeasurements").length())
        val keys = (0 until data.getJSONArray("settings").length()).map { data.getJSONArray("settings").getJSONObject(it).getString("key") }
        assertTrue(Keys.PROFILE in keys)
        assertFalse(Keys.MAP_STYLE_URL in keys)
        // Nombres entiers écrits sans « .0 », comme l'app d'origine.
        assertTrue(json.readText().contains("\"heightCm\": 178,"))
    }
}
