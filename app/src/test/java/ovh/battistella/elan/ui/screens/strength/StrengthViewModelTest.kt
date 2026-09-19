package ovh.battistella.elan.ui.screens.strength

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
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
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.data.repository.SessionUpdate
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.domain.MuscuDraft
import ovh.battistella.elan.domain.ProgressKind
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.templateById
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.tracking.HealthExport
import ovh.battistella.elan.tracking.SavedSessionData
import ovh.battistella.elan.tracking.SessionFinalizer
import ovh.battistella.elan.ui.screens.FakeHeartRatePort
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StrengthViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault())

    /** Miroir Health Connect factice : enregistre ce que le finaliseur lui confie. */
    private class RecordingHealth : HealthExport {
        val exported = mutableListOf<SavedSessionData>()
        override suspend fun export(data: SavedSessionData) { exported += data }
        override suspend fun remove(type: ActivityType, startedAt: Long) = Unit
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

    /** ViewModels créés dans le test : leur chrono est arrêté en fin de test (sinon le ticker infini bloque `runTest`). */
    private val created = mutableListOf<StrengthViewModel>()

    private fun strengthTest(block: suspend TestScope.() -> Unit) = runTest(mainDispatcher.dispatcher) {
        try {
            block()
        } finally {
            created.forEach { it.watch.pause() }
        }
    }

    private fun TestScope.vm(
        template: String? = null,
        add: String? = null,
        sessions: SessionRepository = repos.sessions,
        health: HealthExport? = null,
        heart: FakeHeartRatePort = FakeHeartRatePort(),
        scope: CoroutineScope = backgroundScope,
    ): StrengthViewModel {
        val finalizer = SessionFinalizer(Optional.empty(), Optional.ofNullable(health), scope)
        val vm = StrengthViewModel(
            SavedStateHandle(mapOf("template" to template, "add" to add)),
            sessions,
            repos.settings,
            TestSupport.progressionRunner(repos),
            finalizer,
            heart,
            clock,
            context,
        )
        created += vm
        backgroundScope.launch { vm.ui.collect {} }
        runCurrent()
        return vm
    }

    /** Une séance muscu terminée avec une série « Goblet squat » notée. */
    private suspend fun seedSession(startedAt: Long, weightKg: Double, reps: Int, difficulty: Difficulty?): Long =
        repos.sessions.saveMuscuSession(
            id = null,
            startedAt = startedAt,
            patch = SessionUpdate { endedAt = startedAt + 1_800_000; durationSec = 1800 },
            sets = listOf(TestSupport.muscuSetInput("Goblet squat", 1, reps, weightKg, difficulty)),
        )

    @Test
    fun `hydratation depuis un programme - cibles via la progression auto`() = strengthTest {
        seedSession(now - 7 * 86_400_000L, weightKg = 20.0, reps = 10, difficulty = Difficulty.FACILE)
        val vm = vm(template = "fullbody-a")

        val ui = vm.ui.value
        assertTrue(ui.hydrated)
        assertFalse(ui.paused)
        assertEquals(templateById(TemplateId.FULLBODY_A).exercises.map { it.name }, ui.exercises.map { it.name })

        val goblet = ui.exercises.first { it.name == "Goblet squat" }
        // Facile la semaine passée : +2,5 kg, reps au bas de la fourchette (double progression).
        assertEquals(22.5, goblet.sets.first().weightKg, 1e-9)
        assertEquals(8, goblet.sets.first().reps)
        assertEquals(3, goblet.sets.size)
        assertEquals(2.5, goblet.bump, 1e-9)
        assertEquals(ProgressKind.LOAD, goblet.bumpKind)
        assertEquals(20.0, goblet.lastWeight)
        assertEquals("3 × 8-12", goblet.target)

        // Sans historique : charge de départ, reps au milieu de la fourchette.
        val bench = ui.exercises.first { it.name == "Développé couché haltères" }
        assertEquals(16.0, bench.sets.first().weightKg, 1e-9)
        assertEquals(10, bench.sets.first().reps)
        assertEquals(0.0, bench.bump, 1e-9)

        // Gainage : unité « sec », pas de charge.
        val plank = ui.exercises.first { it.name == "Gainage planche" }
        assertEquals("sec", plank.repUnit)
        assertEquals(0.0, plank.sets.first().weightKg, 1e-9)
        assertNull(plank.lastWeight)

        // Le brouillon est écrit dès le pré-remplissage.
        assertNotNull(repos.settings.snapshot().muscuDraft)
    }

    @Test
    fun `reprise d'un brouillon - état restauré et départ en pause`() = strengthTest {
        val exercises = listOf(
            StrengthExercise(id = "old", name = "Squat", sets = listOf(SetRow(8, 60.0, done = true), SetRow(8, 60.0)), difficulty = Difficulty.MOYEN),
        )
        repos.settings.setMuscuDraft(
            MuscuDraft(startedAt = now - 600_000, elapsedSec = 321.0, exercises = exercisesToJson(exercises), hrSamples = listOf(HrSample(now - 500_000, 120.0))),
        )
        val vm = vm()

        val ui = vm.ui.value
        assertTrue(ui.hydrated)
        assertTrue(ui.paused)
        assertFalse(vm.watch.running.value)
        assertEquals(321, ui.elapsedSec)
        assertEquals(1, ui.exercises.size)
        val squat = ui.exercises.single()
        assertEquals("Squat", squat.name)
        assertTrue(squat.id != "old") // id régénéré
        assertTrue(squat.sets[0].done)
        assertFalse(squat.sets[1].done)
        assertEquals(Difficulty.MOYEN, squat.difficulty)
        assertEquals(1, ui.stats.doneSets)
    }

    @Test
    fun `brouillon réécrit à chaque changement et effacé quand la séance est vide`() = strengthTest {
        val vm = vm()
        assertNull(repos.settings.snapshot().muscuDraft)

        vm.addExercise("  Rowing ")
        runCurrent()
        val draft = repos.settings.snapshot().muscuDraft
        assertNotNull(draft)
        assertEquals(now, draft!!.startedAt)
        val restored = exercisesFromJson(draft.exercises)
        assertEquals("Rowing", restored.single().name)
        assertEquals(listOf(DEFAULT_SET), restored.single().sets)

        val id = vm.ui.value.exercises.single().id
        vm.addSet(id)
        runCurrent()
        assertEquals(2, exercisesFromJson(repos.settings.snapshot().muscuDraft!!.exercises).single().sets.size)

        vm.confirmRemoveExercise(id)
        runCurrent()
        assertTrue(vm.ui.value.exercises.isEmpty())
        assertNull(repos.settings.snapshot().muscuDraft)
    }

    @Test
    fun `cocher une série arme le repos avec la préférence, décocher ne le relance pas`() = strengthTest {
        repos.settings.setRestSeconds(120)
        val vm = vm()
        vm.addExercise("Squat")
        runCurrent()
        val id = vm.ui.value.exercises.single().id
        assertNull(vm.ui.value.restEndsAt)
        assertEquals(120, vm.restDuration)

        vm.toggleSet(id, 0)
        runCurrent()
        assertTrue(vm.ui.value.exercises.single().sets[0].done)
        assertEquals(now + 120_000L, vm.ui.value.restEndsAt)
        assertEquals(1, vm.ui.value.stats.doneSets)

        vm.onRestChange(null)
        vm.toggleSet(id, 0)
        runCurrent()
        assertFalse(vm.ui.value.exercises.single().sets[0].done)
        assertNull(vm.ui.value.restEndsAt)

        // ±15 : la DURÉE préférée bouge (bornée), et elle est persistée.
        vm.adjustRestPreference(15)
        runCurrent()
        assertEquals(135, vm.restDuration)
        assertEquals(135, repos.settings.snapshot().restSeconds)
        repeat(40) { vm.adjustRestPreference(15) }
        assertEquals(600, vm.restDuration)
    }

    @Test
    fun `repos par défaut - repos conseillé de l'objectif, préférence hors bornes ignorée`() = strengthTest {
        // Profil par défaut : hypertrophie → 90 s.
        assertEquals(90, vm().restDuration)
        repos.settings.setRestSeconds(5)
        assertEquals(15, vm().restDuration) // clampé à 15
    }

    @Test
    fun `ressenti - sélection puis re-tap désélectionne`() = strengthTest {
        val vm = vm()
        vm.addExercise("Squat")
        runCurrent()
        val id = vm.ui.value.exercises.single().id

        vm.setDifficulty(id, Difficulty.DUR)
        runCurrent()
        assertEquals(Difficulty.DUR, vm.ui.value.exercises.single().difficulty)
        vm.setDifficulty(id, Difficulty.FACILE)
        runCurrent()
        assertEquals(Difficulty.FACILE, vm.ui.value.exercises.single().difficulty)
        vm.setDifficulty(id, Difficulty.FACILE)
        runCurrent()
        assertNull(vm.ui.value.exercises.single().difficulty)
    }

    @Test
    fun `enregistrement atomique - séries aplaties, brouillon effacé, finaliseur appelé, événement Saved`() = strengthTest {
        val health = RecordingHealth()
        val heart = FakeHeartRatePort(bpm = null)
        val vm = vm(health = health, heart = heart)
        val events = mutableListOf<StrengthEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.addExercise("Squat")
        runCurrent()
        val id = vm.ui.value.exercises.single().id
        vm.addSet(id)
        vm.setWeight(id, 1, 62.5)
        vm.setReps(id, 1, 6)
        vm.toggleSet(id, 0)
        vm.setDifficulty(id, Difficulty.MOYEN)
        runCurrent()
        heart.bpm.value = 130
        runCurrent()
        heart.bpm.value = 150
        runCurrent()
        assertNotNull(repos.settings.snapshot().muscuDraft)

        vm.requestFinish()
        runCurrent()
        assertEquals(StrengthDialog.Finish, vm.ui.value.dialog)
        vm.confirmFinish()
        runCurrent()

        val saved = events.filterIsInstance<StrengthEvent.Saved>().single()
        val session = repos.sessions.getSession(saved.sessionId)!!
        assertEquals(ActivityType.MUSCU, session.type)
        assertEquals(now, session.startedAt)
        assertEquals(now, session.endedAt)
        assertEquals(140.0, session.avgHr)
        assertEquals(150.0, session.maxHr)
        assertEquals("1 exercices · 2 séries · 575 kg soulevés", session.notes)

        val sets = repos.sessions.getMuscuSets(saved.sessionId)
        assertEquals(listOf(1, 2), sets.map { it.setIndex })
        assertEquals(listOf(20.0, 62.5), sets.map { it.weightKg })
        assertEquals(listOf(10, 6), sets.map { it.reps })
        // Ressenti dénormalisé sur chaque série.
        assertEquals(listOf(Difficulty.MOYEN, Difficulty.MOYEN), sets.map { it.difficulty })

        assertNull(repos.settings.snapshot().muscuDraft)
        val exported = health.exported.single()
        assertEquals(ActivityType.MUSCU, exported.type)
        assertEquals(now, exported.startedAt)
        assertEquals(2, exported.hrSamples.size)
    }

    @Test
    fun `échec d'enregistrement - brouillon intact, alerte, réessai possible`() = strengthTest {
        val failing = spyk(repos.sessions)
        coEvery { failing.saveMuscuSession(any(), any(), any(), any()) } throws IllegalStateException("disque plein")
        val vm = vm(sessions = failing)
        val events = mutableListOf<StrengthEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.addExercise("Squat")
        runCurrent()
        vm.requestFinish()
        vm.confirmFinish()
        runCurrent()

        assertEquals(StrengthDialog.SaveFailed, vm.ui.value.dialog)
        assertFalse(vm.ui.value.saving)
        assertTrue(events.filterIsInstance<StrengthEvent.Saved>().isEmpty())
        val draft = repos.settings.snapshot().muscuDraft
        assertNotNull(draft)
        assertEquals("Squat", exercisesFromJson(draft!!.exercises).single().name)
        assertTrue(repos.sessions.listSessions().isEmpty())
    }

    @Test
    fun `terminer une séance vide - alerte sans enregistrement`() = strengthTest {
        val vm = vm()
        vm.requestFinish()
        runCurrent()
        assertEquals(StrengthDialog.EmptyFinish, vm.ui.value.dialog)
        vm.dismissDialog()
        runCurrent()
        assertEquals(StrengthDialog.None, vm.ui.value.dialog)
        assertTrue(repos.sessions.listSessions().isEmpty())
    }

    @Test
    fun `quitter - vide sort directement, sinon dialogue pause ou abandon`() = strengthTest {
        val vm = vm()
        val events = mutableListOf<StrengthEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.requestExit()
        runCurrent()
        assertEquals(listOf<StrengthEvent>(StrengthEvent.Exit), events)

        vm.addExercise("Squat")
        runCurrent()
        vm.requestExit()
        runCurrent()
        assertEquals(StrengthDialog.Exit, vm.ui.value.dialog)

        vm.pauseAndExit()
        runCurrent()
        assertTrue(vm.ui.value.paused)
        assertNotNull(repos.settings.snapshot().muscuDraft)
        assertEquals(2, events.size)

        vm.abandon()
        runCurrent()
        assertNull(repos.settings.snapshot().muscuDraft)
        assertEquals(3, events.size)

        // L'écran qui se ferme rappelle onStop : la séance abandonnée ne doit pas renaître.
        vm.onStop()
        runCurrent()
        assertNull(repos.settings.snapshot().muscuDraft)
    }

    @Test
    fun `ajout depuis le catalogue - recommandation puis dernière charge enregistrée`() = strengthTest {
        val vm = vm(add = "goblet-squat")
        val ex = vm.ui.value.exercises.single()
        assertEquals("Goblet squat", ex.name)
        // Profil défaut 70 kg, hypertrophie : 0,3 × 70 × 1 = 21 → 21 kg (pas 1 kg), 4 séries de 10.
        assertEquals(4, ex.sets.size)
        assertEquals(21.0, ex.sets.first().weightKg, 1e-9)
        assertEquals(10, ex.sets.first().reps)
        assertEquals("4 × 8-12", ex.target)
        assertNull(ex.lastWeight)

        seedSession(now - 86_400_000L, weightKg = 24.0, reps = 10, difficulty = null)
        vm.addCatalogExercise(ovh.battistella.elan.domain.catalogById("goblet-squat")!!)
        runCurrent()
        val again = vm.ui.value.exercises.last()
        assertEquals(24.0, again.sets.first().weightKg, 1e-9)
        assertEquals(24.0, again.lastWeight)
        assertEquals(2, vm.ui.value.exercises.size)
    }

    @Test
    fun `retirer une série - cochée demande confirmation, non cochée part directement`() = strengthTest {
        val vm = vm()
        vm.addExercise("Squat")
        runCurrent()
        val id = vm.ui.value.exercises.single().id
        vm.addSet(id)
        vm.requestRemoveSet(id, 1)
        runCurrent()
        assertEquals(1, vm.ui.value.exercises.single().sets.size)

        vm.toggleSet(id, 0)
        vm.requestRemoveSet(id, 0)
        runCurrent()
        assertEquals(StrengthDialog.RemoveSet(id, 0), vm.ui.value.dialog)
        vm.confirmRemoveSet(id, 0)
        runCurrent()
        assertTrue(vm.ui.value.exercises.single().sets.isEmpty())
        runCurrent()
        // Plus aucune série : le brouillon est effacé.
        assertNull(repos.settings.snapshot().muscuDraft)
    }

    @Test
    fun `sérialisation JSON du brouillon - aller-retour et brouillon d'origine`() {
        val ex = StrengthExercise(
            id = "x", name = "Fentes", sets = listOf(SetRow(10, 12.5, true)), target = "3 × 10 / jambe", repUnit = "reps",
            lastWeight = 12.5, howTo = "Un pas", muscles = listOf("Quadriceps"), icon = "run", imageKey = "dumbbell-lunges",
            difficulty = Difficulty.DUR, bump = -2.5, bumpKind = ProgressKind.LOAD,
        )
        val back = exercisesFromJson(exercisesToJson(listOf(ex))).single()
        assertEquals(ex.copy(id = back.id), back)

        // Brouillon de l'app d'origine : champs facultatifs absents, `done` omis.
        val legacy = JSONArray("""[{"id":"e0","name":"Tractions","sets":[{"reps":8,"weightKg":0}]}]""")
        val restored = exercisesFromJson(legacy).single()
        assertEquals("Tractions", restored.name)
        assertEquals(listOf(SetRow(8, 0.0, false)), restored.sets)
        assertEquals("reps", restored.repUnit)
        assertNull(restored.target)
        assertTrue(restored.muscles.isEmpty())
    }

    @Test
    fun `bumpHint - montée et allègement, kg et secondes`() {
        assertEquals("Auto : +2,5 kg cette semaine", bumpHint(2.5, ProgressKind.LOAD))
        assertEquals("Allégé : −2,5 kg cette semaine", bumpHint(-2.5, ProgressKind.LOAD))
        assertEquals("Auto : +5 s cette semaine", bumpHint(5.0, ProgressKind.TIME))
    }

    @Test
    fun `échantillons FC ignorés en pause`() = strengthTest {
        val heart = FakeHeartRatePort(bpm = null)
        val health = RecordingHealth()
        val vm = vm(heart = heart, health = health)
        vm.addExercise("Squat")
        runCurrent()
        vm.pause()
        heart.bpm.value = 140
        runCurrent()
        vm.resume()
        runCurrent()
        vm.requestFinish()
        vm.confirmFinish()
        runCurrent()
        val id = repos.sessions.listSessions().first().id
        assertNull(repos.sessions.getSession(id)!!.avgHr)
        assertTrue(health.exported.single().hrSamples.isEmpty())
    }
}
