package ovh.battistella.elan.ui.screens.session

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import ovh.battistella.elan.R
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.RecordKind
import ovh.battistella.elan.data.repository.RecordScope
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.settings.FakeExportPort
import ovh.battistella.elan.tracking.HealthExport
import ovh.battistella.elan.tracking.SavedSessionData
import ovh.battistella.elan.tracking.SessionFinalizer
import java.util.Optional

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDetailViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val t0 = 1_700_000_000_000L
    private val finalizer = SessionFinalizer(Optional.empty(), Optional.empty(), CoroutineScope(Dispatchers.Unconfined))
    private val export = FakeExportPort()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vm(id: Long) = SessionDetailViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions, repos.settings, SnackbarController(), context, finalizer, export)

    private suspend fun seedVelo(): Long {
        val id = db.sessionDao().insert(
            TestSupport.session(type = ActivityType.VELO, startedAt = t0, distanceM = 30_000.0, avgSpeedKmh = 25.0, calories = 600.0),
        )
        db.trackPointDao().insertAll(
            listOf(
                TestSupport.trackPoint(id, ts = t0, hr = 120.0),
                TestSupport.trackPoint(id, ts = t0 + 10_000, lat = 48.857, lon = 2.3525, hr = 130.0, cadence = 85.0),
                TestSupport.trackPoint(id, ts = t0 + 20_000, lat = 48.858, lon = 2.353, hr = 140.0, cadence = 90.0),
            ),
        )
        return id
    }

    private suspend fun seedMuscu(): Long {
        val id = db.sessionDao().insert(TestSupport.session(type = ActivityType.MUSCU, startedAt = t0 + 86_400_000L, durationSec = 2400))
        db.muscuSetDao().insertAll(
            listOf(
                TestSupport.muscuSet(id, "Goblet squat", 1, 10, 20.0, Difficulty.FACILE),
                TestSupport.muscuSet(id, "Goblet squat", 2, 9, 22.0, Difficulty.FACILE),
                TestSupport.muscuSet(id, "Gainage planche", 1, 30, 0.0, Difficulty.DUR),
            ),
        )
        return id
    }

    @Test
    fun `séance GPS - points, records, FC max du profil`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val vm = vm(id)
        advanceUntilIdle()

        val ui = vm.ui.value
        assertFalse(ui.loading)
        assertEquals(ActivityType.VELO, ui.session?.type)
        assertEquals(3, ui.points.size)
        assertTrue(ui.sets.isEmpty())
        // Seule séance vélo : records absolus sur chaque métrique connue.
        assertTrue(ui.records.contains(SessionRecord(RecordKind.DISTANCE, RecordScope.ALL)))
        assertTrue(ui.records.contains(SessionRecord(RecordKind.SPEED, RecordScope.ALL)))
        assertEquals(190.0, ui.maxHr, 0.0)
    }

    @Test
    fun `séance muscu - séries chargées, pas de points`() = runTest(mainDispatcher.dispatcher) {
        val id = seedMuscu()
        val vm = vm(id)
        advanceUntilIdle()

        val ui = vm.ui.value
        assertEquals(ActivityType.MUSCU, ui.session?.type)
        assertEquals(3, ui.sets.size)
        assertTrue(ui.points.isEmpty())
        val groups = groupSets(ui.sets)
        assertEquals(listOf("Goblet squat", "Gainage planche"), groups.map { it.name })
        assertEquals(398.0, groups[0].volume, 0.0)
        assertEquals(Difficulty.FACILE, groups[0].difficulty)
        assertEquals(Difficulty.DUR, groups[1].difficulty)
    }

    @Test
    fun `séance introuvable`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm(999)
        advanceUntilIdle()
        assertFalse(vm.ui.value.loading)
        assertNull(vm.ui.value.session)
    }

    @Test
    fun `retype refusé pour la muscu et vers le même type`() = runTest(mainDispatcher.dispatcher) {
        val muscu = vm(seedMuscu())
        advanceUntilIdle()
        muscu.requestRetype(ActivityType.COURSE)
        assertEquals(SessionDialog.None, muscu.ui.value.dialog)

        val velo = vm(seedVelo())
        advanceUntilIdle()
        velo.requestRetype(ActivityType.VELO)
        assertEquals(SessionDialog.None, velo.ui.value.dialog)
        velo.requestRetype(ActivityType.MUSCU)
        assertEquals(SessionDialog.None, velo.ui.value.dialog)
    }

    @Test
    fun `retype vélo vers course - confirmation, écriture, rechargement`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val vm = vm(id)
        val events = mutableListOf<SessionEvent>()
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.requestRetype(ActivityType.COURSE)
        assertEquals(SessionDialog.Retype(ActivityType.COURSE), vm.ui.value.dialog)
        // Double appui : pas de second dialogue empilé (garde de réentrance).
        vm.requestRetype(ActivityType.MARCHE)
        assertEquals(SessionDialog.Retype(ActivityType.COURSE), vm.ui.value.dialog)

        vm.dismissDialog()
        assertEquals(SessionDialog.None, vm.ui.value.dialog)
        assertEquals(ActivityType.VELO, repos.sessions.getSession(id)?.type)

        vm.requestRetype(ActivityType.COURSE)
        vm.confirmRetype()
        advanceUntilIdle()
        assertEquals(ActivityType.COURSE, vm.ui.value.session?.type)
        assertEquals(ActivityType.COURSE, repos.sessions.getSession(id)?.type)
        assertFalse(vm.ui.value.busy)
        assertEquals(listOf<SessionEvent>(SessionEvent.RetypeSucceeded), events)
        // Les records sont recalculés pour le nouveau type.
        assertNotNull(vm.ui.value.records.firstOrNull { it.kind == RecordKind.DISTANCE })
        eventsJob.cancel()
    }

    @Test
    fun `retype confirmé - miroir Health Connect retiré sous l'ancien type puis réécrit`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val calls = mutableListOf<String>()
        val health = object : HealthExport {
            override suspend fun export(data: SavedSessionData) {
                calls += "export:${data.type.key}:${data.startedAt}:${data.hrSamples.size}"
            }
            override suspend fun remove(type: ActivityType, startedAt: Long) {
                calls += "remove:${type.key}:$startedAt"
            }
        }
        val finalizer = SessionFinalizer(Optional.empty(), Optional.of(health), CoroutineScope(Dispatchers.Unconfined))
        val vm = SessionDetailViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions, repos.settings, SnackbarController(), context, finalizer, export)
        advanceUntilIdle()

        vm.requestRetype(ActivityType.COURSE)
        vm.confirmRetype()
        advanceUntilIdle()

        // L'ancien type d'abord (l'identifiant client porte le type), puis la
        // séance réécrite sous le nouveau, avec ses échantillons FC.
        assertEquals(listOf("remove:velo:$t0", "export:course:$t0:3"), calls)
    }

    @Test
    fun `suppression confirmée`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val vm = vm(id)
        val events = mutableListOf<SessionEvent>()
        val eventsJob = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.requestDelete()
        assertEquals(SessionDialog.Delete, vm.ui.value.dialog)
        vm.dismissDialog()
        assertNotNull(repos.sessions.getSession(id))

        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(repos.sessions.getSession(id))
        assertTrue(repos.sessions.getTrackPoints(id).isEmpty())
        assertEquals(listOf<SessionEvent>(SessionEvent.Deleted), events)
        eventsJob.cancel()
    }

    @Test
    fun `records mis en avant - absolus d'abord, trois au plus`() {
        val records = listOf(
            SessionRecord(RecordKind.DURATION, RecordScope.YEAR),
            SessionRecord(RecordKind.DISTANCE, RecordScope.ALL),
            SessionRecord(RecordKind.SPEED, RecordScope.YEAR),
            SessionRecord(RecordKind.ELEVATION, RecordScope.ALL),
        )
        val top = topRecords(records)
        assertEquals(3, top.size)
        assertEquals(listOf(RecordScope.ALL, RecordScope.ALL, RecordScope.YEAR), top.map { it.scope })
    }

    @Test
    fun `groupes d'exercices - ordre d'apparition et ressenti de la première ligne`() {
        val sets = listOf(
            MuscuSet(1, 1, "B", 1, 8, 10.0, null),
            MuscuSet(2, 1, "A", 1, 8, 10.0, Difficulty.MOYEN),
            MuscuSet(3, 1, "B", 2, 8, 10.0, null),
        )
        val groups = groupSets(sets)
        assertEquals(listOf("B", "A"), groups.map { it.name })
        assertEquals(2, groups[0].rows.size)
        assertNull(groups[0].difficulty)
        assertEquals(Difficulty.MOYEN, groups[1].difficulty)
    }

    @Test
    fun `export GPX - fichier partagé avec la zone de confidentialité, rien sous 2 points`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        repos.settings.setPrivacyZoneM(200.0)
        val snackbar = SnackbarController()
        val messages = mutableListOf<String>()
        val job = launch { snackbar.messages.collect { messages += it.text } }
        val vm = SessionDetailViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions, repos.settings, snackbar, context, finalizer, export)
        advanceUntilIdle()

        vm.exportGpx()
        advanceUntilIdle()

        assertEquals(listOf("gpx:$id:200"), export.calls)
        assertEquals("application/gpx+xml", export.shared.single().second)
        assertFalse(vm.ui.value.exporting)
        assertTrue(messages.isEmpty())

        // Sortie sans tracé exploitable : ni fichier ni partage, un message.
        val short = db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = t0 + 1))
        db.trackPointDao().insertAll(listOf(TestSupport.trackPoint(short, ts = t0 + 1)))
        val vm2 = SessionDetailViewModel(SavedStateHandle(mapOf("id" to short)), repos.sessions, repos.settings, snackbar, context, finalizer, export)
        advanceUntilIdle()
        vm2.exportGpx()
        advanceUntilIdle()

        assertEquals(1, export.calls.size)
        assertEquals(1, export.shared.size)
        assertEquals(listOf(context.getString(R.string.session_gpx_no_track)), messages)
        job.cancel()
    }

    @Test
    fun `export GPX - l exporteur rend null ou échoue - message et pas de partage`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val snackbar = SnackbarController()
        val messages = mutableListOf<String>()
        val job = launch { snackbar.messages.collect { messages += it.text } }
        val vm = SessionDetailViewModel(SavedStateHandle(mapOf("id" to id)), repos.sessions, repos.settings, snackbar, context, finalizer, export)
        advanceUntilIdle()

        export.gpxResult = null
        vm.exportGpx()
        advanceUntilIdle()
        assertTrue(export.shared.isEmpty())
        assertEquals(context.getString(R.string.session_gpx_no_track), messages.last())

        export.failure = IllegalStateException("Export indisponible.")
        vm.exportGpx()
        advanceUntilIdle()
        assertTrue(export.shared.isEmpty())
        assertEquals("Export indisponible.", messages.last())
        assertFalse(vm.ui.value.exporting)
        job.cancel()
    }

    @Test
    fun `partage - aperçu ouvert puis PNG partagé et aperçu fermé`() = runTest(mainDispatcher.dispatcher) {
        val id = seedVelo()
        val vm = vm(id)
        advanceUntilIdle()

        vm.share()
        assertTrue(vm.ui.value.sharePreview)

        // Bitmap 3 arguments : la seule fabrique simulée par Robolectric (mode graphique hérité).
        vm.shareImage(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap())
        // L'écriture du PNG passe par Dispatchers.IO : on attend la fin réelle.
        vm.ui.first { !it.sharing }

        val (file, mime, _) = export.shared.single()
        assertEquals("image/png", mime)
        assertTrue(file.exists() && file.length() > 0)
        assertFalse(vm.ui.value.sharePreview)
        assertFalse(vm.ui.value.sharing)
    }
}
