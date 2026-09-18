package ovh.battistella.elan.sync

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.data.settings.AutoProgressionConfig
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.testing.TestSupport
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AutoProgressionRunnerTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val zone: ZoneId = ZoneId.systemDefault()
    private val monday = LocalDate.of(2026, 9, 14).atStartOfDay(zone).plusHours(9).toInstant().toEpochMilli()
    private val thursday = monday + 3 * 86_400_000L
    private val nextMonday = monday + 7 * 86_400_000L

    /** Notification factice : journalise et, sur demande, échoue. */
    private class RecordingNotifier(private val fail: Boolean = false) : ProgressionNotify {
        val posted = mutableListOf<Pair<String, String>>()
        override fun notify(title: String, body: String) {
            if (fail) throw IllegalStateException("gestionnaire indisponible")
            posted += title to body
        }
    }

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun runner(notifier: ProgressionNotify = RecordingNotifier()) = TestSupport.progressionRunner(repos, notifier)

    private suspend fun seed(exercise: String, startedAt: Long, weightKg: Double, reps: Int, difficulty: Difficulty?) {
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = listOf(TestSupport.muscuSetInput(exercise, 1, reps, weightKg, difficulty)),
        )
    }

    @Test
    fun `targetsForExercises - progression active puis désactivée`() = runTest {
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)
        val exercises = templateById(TemplateId.FULLBODY_A).exercises

        val targets = runner().targetsForExercises(exercises)
        assertEquals(exercises.map { it.name }, targets.keys.toList())
        assertEquals(22.5, targets.getValue("Goblet squat").weightKg, 1e-9)
        assertEquals(2.5, targets.getValue("Goblet squat").bump, 1e-9)
        assertEquals(16.0, targets.getValue("Développé couché haltères").weightKg, 1e-9)

        repos.settings.setAutoProgression(AutoProgressionConfig(enabled = false))
        val off = runner().targetsForExercises(exercises)
        assertEquals(20.0, off.getValue("Goblet squat").weightKg, 1e-9)
        assertEquals(0.0, off.getValue("Goblet squat").bump, 1e-9)
    }

    @Test
    fun `amorçage silencieux - première semaine mémorisée sans annonce`() = runTest {
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)
        val notifier = RecordingNotifier()

        val changes = runner(notifier).runWeeklyProgressionIfDue(monday)

        assertTrue(changes.isEmpty())
        assertTrue(notifier.posted.isEmpty())
        val state = repos.settings.snapshot().autoProgressionState
        assertEquals(isoWeekKey(monday, zone), state.week)
        assertTrue(state.changes.isEmpty())
        assertTrue(state.dismissed)
    }

    @Test
    fun `nouvelle semaine - changements calculés, persistés puis notifiés, idempotent dans la semaine`() = runTest {
        repos.settings.setAutoProgressionState(AutoProgressionState(week = "2026-W01", changes = emptyList(), dismissed = true))
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)
        seed("Gainage planche", monday - 86_400_000L, 0.0, 30, Difficulty.DUR)
        val notifier = RecordingNotifier()
        val runner = runner(notifier)

        val changes = runner.runWeeklyProgressionIfDue(monday)

        assertEquals(
            listOf(
                ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP),
                ProgressionChange("Gainage planche", ProgressKind.TIME, 30.0, 25.0, Direction.DOWN),
            ),
            changes,
        )
        val state = repos.settings.snapshot().autoProgressionState
        assertEquals(isoWeekKey(monday, zone), state.week)
        assertEquals(changes, state.changes)
        assertFalse(state.dismissed)
        assertEquals(1, notifier.posted.size)
        assertEquals("Ton programme évolue cette semaine", notifier.posted.single().first)
        assertEquals("Goblet squat 20 → 22,5 kg, Gainage planche 30 → 25 s.", notifier.posted.single().second)

        // Même semaine : on renvoie l'état sans recalculer ni renotifier.
        seed("Goblet squat", thursday - 3_600_000L, 22.5, 8, Difficulty.FACILE)
        assertEquals(changes, runner.runWeeklyProgressionIfDue(thursday))
        assertEquals(1, notifier.posted.size)

        // Semaine suivante : nouvelle évaluation depuis la dernière valeur ENREGISTRÉE.
        val next = runner.runWeeklyProgressionIfDue(nextMonday)
        assertEquals(
            listOf(
                ProgressionChange("Goblet squat", ProgressKind.LOAD, 22.5, 25.0, Direction.UP),
                // Le gainage n'a pas été refait : même allègement reproposé (pas de dérive).
                ProgressionChange("Gainage planche", ProgressKind.TIME, 30.0, 25.0, Direction.DOWN),
            ),
            next,
        )
        assertEquals(2, notifier.posted.size)
    }

    @Test
    fun `l'état est persisté avant la notification - un échec de notif ne rejoue pas l'annonce`() = runTest {
        repos.settings.setAutoProgressionState(AutoProgressionState(week = "2026-W01", changes = emptyList(), dismissed = true))
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)

        val changes = runner(RecordingNotifier(fail = true)).runWeeklyProgressionIfDue(monday)

        assertEquals(1, changes.size)
        val state = repos.settings.snapshot().autoProgressionState
        assertEquals(isoWeekKey(monday, zone), state.week)
        assertEquals(changes, state.changes)
        // Rejouer la même semaine ne recalcule pas.
        val ok = RecordingNotifier()
        assertEquals(changes, runner(ok).runWeeklyProgressionIfDue(thursday))
        assertTrue(ok.posted.isEmpty())
    }

    @Test
    fun `aucun changement - dismissed vrai et pas de notification`() = runTest {
        repos.settings.setAutoProgressionState(AutoProgressionState(week = "2026-W01"))
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, null) // non noté : pas de progression
        val notifier = RecordingNotifier()

        assertTrue(runner(notifier).runWeeklyProgressionIfDue(monday).isEmpty())
        val state = repos.settings.snapshot().autoProgressionState
        assertEquals(isoWeekKey(monday, zone), state.week)
        assertTrue(state.dismissed)
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `désactivée - rien n'est évalué ni persisté`() = runTest {
        repos.settings.setAutoProgression(AutoProgressionConfig(enabled = false))
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)

        assertTrue(runner().runWeeklyProgressionIfDue(monday).isEmpty())
        assertEquals("", repos.settings.snapshot().autoProgressionState.week)
    }

    @Test
    fun `computeWeeklyChanges - planning effectif, exercices auto dédupliqués, jamais les programmes doux`() = runTest {
        // Full-body A deux fois dans la semaine + dos/lombaire : Goblet squat ne compte qu'une fois,
        // les exercices de renfort doux ne sont jamais touchés.
        repos.settings.setCustomWeekPlan(
            listOf(
                PlannedSession.Muscu("Full-body A", TemplateId.FULLBODY_A),
                PlannedSession.Muscu("Full-body A", TemplateId.FULLBODY_A),
                PlannedSession.Muscu("Dos", TemplateId.DOS_LOMBAIRE),
                PlannedSession.Repos,
                PlannedSession.Repos,
                PlannedSession.Repos,
                PlannedSession.Repos,
            ),
        )
        seed("Goblet squat", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)
        seed("Pont fessier", monday - 86_400_000L, 0.0, 12, Difficulty.FACILE)
        // Full-body B n'est pas au planning : ignoré même avec historique.
        seed("Soulevé de terre roumain haltères", monday - 86_400_000L, 20.0, 10, Difficulty.FACILE)

        val changes = runner().computeWeeklyChanges()

        assertEquals(listOf("Goblet squat"), changes.map { it.exercise })
        assertEquals(ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP), changes.single())
    }
}
