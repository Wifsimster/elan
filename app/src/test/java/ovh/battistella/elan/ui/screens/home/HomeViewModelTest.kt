package ovh.battistella.elan.ui.screens.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.AutoProgressionState
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Direction
import ovh.battistella.elan.domain.Goal
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.ProgressionChange
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.isoWeekKey
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.components.Tone
import ovh.battistella.elan.ui.screens.FakeHeartRatePort
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val zone: ZoneId = ZoneId.systemDefault()

    // Mercredi 16 septembre 2026, midi (heure locale) : jour de repos du planning par défaut.
    private val wednesday: Long = LocalDate.of(2026, 9, 16).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    private val tuesday: Long = LocalDate.of(2026, 9, 15).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private val hour = 3_600_000L
    private val day = 24 * hour

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(now: Long = wednesday, heart: FakeHeartRatePort = FakeHeartRatePort()) = HomeViewModel(
        sessions = repos.sessions,
        settings = repos.settings,
        heartRate = heart,
        clock = Clock.fixed(java.time.Instant.ofEpochMilli(now), zone),
        snackbar = SnackbarController(),
        context = ApplicationProvider.getApplicationContext<Context>(),
    )

    private suspend fun seed(type: ActivityType, startedAt: Long, distanceM: Double? = null, calories: Double? = null): Long =
        db.sessionDao().insert(
            TestSupport.session(type = type, startedAt = startedAt, durationSec = 1800, distanceM = distanceM, calories = calories),
        )

    @Test
    fun `stats de la semaine et de la semaine précédente`() = runTest(mainDispatcher.dispatcher) {
        // Cette semaine : lundi 14 et mardi 15 ; semaine dernière : jeudi 10.
        seed(ActivityType.VELO, wednesday - 2 * day, distanceM = 20_000.0, calories = 400.0)
        seed(ActivityType.MUSCU, wednesday - day)
        seed(ActivityType.VELO, wednesday - 6 * day, distanceM = 30_000.0, calories = 700.0)

        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()

        val ui = vm.ui.value
        assertTrue(ui.loaded)
        assertEquals(2, ui.stats?.sessionCount)
        assertEquals(20_000.0, ui.stats?.totalDistanceM)
        assertEquals(1, ui.lastStats?.sessionCount)
        assertEquals(30_000.0, ui.lastStats?.totalDistanceM)
        // 7 barres, aujourd'hui (mercredi) à droite.
        assertEquals(7, ui.bars.size)
        assertEquals("M", ui.bars.last().label)
        assertEquals(1800, ui.bars[ui.bars.lastIndex - 1].value)
        // 3 séances récentes au plus, la plus récente d'abord.
        assertEquals(3, ui.recent.size)
        assertEquals(ActivityType.MUSCU, ui.recent.first().type)
    }

    @Test
    fun `séance du jour repos le mercredi et muscu le mardi`() = runTest(mainDispatcher.dispatcher) {
        val wed = vm(wednesday)
        backgroundScope.launch { wed.ui.collect {} }
        advanceUntilIdle()
        assertEquals(PlannedSession.Repos, wed.ui.value.today)
        assertEquals(3, wed.ui.value.jsDay)

        val tue = vm(tuesday)
        backgroundScope.launch { tue.ui.collect {} }
        advanceUntilIdle()
        assertEquals(PlannedSession.Muscu("Full-body A", TemplateId.FULLBODY_A), tue.ui.value.today)
    }

    @Test
    fun `séance du jour suit le planning personnalisé`() = runTest(mainDispatcher.dispatcher) {
        val plan = List(7) { PlannedSession.Outing("course", "Footing") }
        repos.settings.setCustomWeekPlan(plan)

        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertEquals(PlannedSession.Outing("course", "Footing"), vm.ui.value.today)
    }

    @Test
    fun `brouillon muscu présent = reprenable`() = runTest(mainDispatcher.dispatcher) {
        repos.settings.setSetting("muscu_draft", """{"version":1,"startedAt":1,"elapsedSec":10,"exercises":[]}""")
        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertTrue(vm.ui.value.resumable)
    }

    @Test
    fun `onboarding affiché sans marqueur puis masqué après C'est parti`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertNotNull(vm.ui.value.onboarding)
        assertEquals(70.0, vm.ui.value.onboarding?.weightKg)

        vm.finishOnboarding(weightKg = 82, heightCm = 180, maxHr = 185, goal = TrainingGoal.FORCE)
        advanceUntilIdle()

        assertNull(vm.ui.value.onboarding)
        val profile = repos.settings.getProfile()
        assertEquals(82.0, profile.weightKg, 0.0)
        assertEquals(180.0, profile.heightCm, 0.0)
        assertEquals(185.0, profile.maxHr, 0.0)
        assertEquals(TrainingGoal.FORCE, profile.goal)
        assertEquals("1", repos.settings.getSetting("onboarding_done"))
    }

    @Test
    fun `onboarding absent quand le marqueur existe`() = runTest(mainDispatcher.dispatcher) {
        repos.settings.setOnboardingDone(true)
        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertNull(vm.ui.value.onboarding)
    }

    @Test
    fun `objectifs mesurés sur la période courante`() = runTest(mainDispatcher.dispatcher) {
        repos.settings.setGoals(
            listOf(
                Goal("g1", GoalMetric.SESSIONS, GoalPeriod.WEEK, 3.0, GoalActivity.ALL),
                Goal("g2", GoalMetric.DISTANCE, GoalPeriod.WEEK, 10.0, GoalActivity.VELO),
            ),
        )
        seed(ActivityType.VELO, wednesday - day, distanceM = 12_345.0)
        seed(ActivityType.VELO, wednesday - 8 * day, distanceM = 50_000.0) // semaine dernière : hors période

        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()

        val goals = vm.ui.value.goals
        assertEquals(2, goals.size)
        assertEquals(1.0, goals[0].value, 0.0)
        assertFalse(goals[0].done)
        assertEquals(12.3, goals[1].value, 0.0)
        assertTrue(goals[1].done)
    }

    @Test
    fun `bannière de progression visible cette semaine puis masquée`() = runTest(mainDispatcher.dispatcher) {
        val change = ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP)
        repos.settings.setAutoProgressionState(AutoProgressionState(week = isoWeekKey(wednesday, zone), changes = listOf(change)))

        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertNotNull(vm.ui.value.planUpdate)

        vm.dismissPlanUpdate()
        advanceUntilIdle()
        assertNull(vm.ui.value.planUpdate)
        assertTrue(repos.settings.snapshot().autoProgressionState.dismissed)
    }

    @Test
    fun `bannière absente pour une autre semaine`() = runTest(mainDispatcher.dispatcher) {
        val change = ProgressionChange("Goblet squat", ProgressKind.LOAD, 20.0, 22.5, Direction.UP)
        repos.settings.setAutoProgressionState(AutoProgressionState(week = "2020-W01", changes = listOf(change)))
        val vm = vm()
        backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        assertNull(vm.ui.value.planUpdate)
    }

    @Test
    fun `pastille cardio suit le port`() = runTest(mainDispatcher.dispatcher) {
        val heart = FakeHeartRatePort(bpm = 132, connected = true)
        val vm = vm(heart = heart)
        backgroundScope.launch { vm.heart.collect {} }
        advanceUntilIdle()
        assertEquals(HeartUi(132, true), vm.heart.value)
        heart.connected.value = false
        heart.bpm.value = null
        advanceUntilIdle()
        assertEquals(HeartUi(null, false), vm.heart.value)
    }

    @Test
    fun `tendance stable positive négative`() {
        assertEquals(Tone.Neutral, buildTrend(3.0, 3.0, "stable") { it.toString() }.tone)
        assertEquals("stable", buildTrend(3.0, 3.0, "stable") { it.toString() }.label)
        val up = buildTrend(5.0, 3.0, "stable") { it.toInt().toString() }
        assertEquals("+2", up.label)
        assertEquals(Tone.Positive, up.tone)
        val down = buildTrend(1.0, 3.0, "stable") { it.toInt().toString() }
        assertEquals("−2", down.label)
        assertEquals(Tone.Negative, down.tone)
    }
}
