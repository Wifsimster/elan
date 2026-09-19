package ovh.battistella.elan.tracking

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.testing.TestSupport
import java.util.Optional

/**
 * Cycle de vie complet d'une sortie sur une vraie base Room en mémoire, avec
 * une source GPS et des capteurs pilotés par le test et un temps virtuel
 * (chrono, flush périodique, horodatages des fixes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val gps = FakeGpsSource()
    private val hr = FakeHeartRate()
    private val csc = FakeCadence()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun TestScope.controller(sessions: SessionRepository = repos.sessions): TrackingController {
        val clock = SchedulerClock(testScheduler, BASE_MS)
        val c = TrackingController(
            context = context,
            sessions = sessions,
            settings = repos.settings,
            gps = gps,
            heartRate = Optional.of(hr),
            cadence = Optional.of(csc),
            finalizer = SessionFinalizer(Optional.empty(), Optional.empty(), backgroundScope),
            clock = clock,
            scope = backgroundScope,
        )
        runCurrent() // abonnements capteurs et chrono en place
        return c
    }

    private fun TestScope.now(): Long = BASE_MS + testScheduler.currentTime

    /** Démarre une sortie et laisse le flux GPS s'abonner. */
    private suspend fun TestScope.start(c: TrackingController, type: ActivityType = ActivityType.VELO): Long {
        assertEquals(BeginResult.STARTED, c.begin(type))
        runCurrent()
        return c.state.value.sessionId!!
    }

    /** Pousse un fix à `northM` du départ, puis avance le temps d'une seconde. */
    private suspend fun TestScope.fix(northM: Double, accuracy: Double? = 5.0, speedMs: Double? = 5.0) {
        gps.flow.emit(fixAt(now(), northM, speedMs = speedMs, accuracy = accuracy))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
    }

    /** Un tracé rectiligne de `count` fixes espacés de 5 m à 1 Hz (18 km/h). */
    private suspend fun TestScope.straightTrack(count: Int, fromM: Double = 0.0) {
        for (i in 0 until count) fix(fromM + i * STEP_M)
    }

    @Test
    fun `begin crée une séance en cours et passe en phase active`() = runTest {
        val c = controller()

        val id = start(c)

        val s = c.state.value
        assertEquals(OutingPhase.ACTIVE, s.phase)
        assertEquals(ActivityType.VELO, s.type)
        assertEquals(GpsStatus.TRACKING, s.gpsStatus)
        assertEquals(BASE_MS, s.startedAt)
        val row = repos.sessions.getSession(id)!!
        assertNull(row.endedAt)
        assertEquals(ActivityType.VELO, row.type)
        // Le service de premier plan a été demandé.
        val started = shadowOf(context as Application).nextStartedService
        assertEquals(TrackingService::class.java.name, started.component?.className)
    }

    @Test
    fun `sans permission précise rien ne démarre`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val c = controller()

        assertEquals(BeginResult.DENIED, c.begin(ActivityType.COURSE))
        assertEquals(OutingPhase.IDLE, c.state.value.phase)
        assertEquals(GpsStatus.DENIED, c.state.value.gpsStatus)
        assertEquals(TrackingController.ERROR_DENIED, c.state.value.error)
        assertTrue(repos.sessions.listInProgressSessions().isEmpty())

        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(BeginResult.COARSE, c.begin(ActivityType.COURSE))
        assertEquals(TrackingController.ERROR_COARSE, c.state.value.error)
    }

    @Test
    fun `un fix imprécis est rejeté mais met à jour la précision`() = runTest {
        val c = controller()
        start(c)

        fix(0.0, accuracy = 80.0)

        val s = c.state.value
        assertEquals(0, s.pointCount)
        assertEquals(0.0, s.distanceM, 0.0)
        assertEquals(80.0, s.accuracyM!!, 0.0)
        assertTrue(s.livePath.isEmpty())
    }

    @Test
    fun `la distance s'accumule sur un tracé rectiligne de 100 m`() = runTest {
        val c = controller()
        start(c)

        straightTrack(21) // 0 → 100 m

        val s = c.state.value
        assertEquals(21, s.pointCount)
        assertTrue("distance ${s.distanceM}", s.distanceM in 80.0..105.0)
        assertEquals(18.0, s.speedKmh, 0.01)
        assertEquals(18.0, s.maxSpeedKmh, 0.01)
        assertEquals(5.0, s.accuracyM!!, 0.0)
        assertEquals(21, s.elapsedSec)
        assertTrue("calories ${s.caloriesLive}", s.caloriesLive > 0)
    }

    @Test
    fun `une vitesse Doppler aberrante n'entre pas dans la vitesse max`() = runTest {
        val c = controller()
        start(c)

        fix(0.0)
        fix(5.0, speedMs = 40.0) // 144 km/h : glitch

        assertEquals(18.0, c.state.value.maxSpeedKmh, 0.01)
    }

    @Test
    fun `la pause gèle les cumuls et la reprise ne saute pas`() = runTest {
        val c = controller()
        start(c)
        straightTrack(11) // 0 → 50 m
        val beforePause = c.state.value

        c.pause()
        runCurrent()
        assertEquals(OutingPhase.PAUSED, c.state.value.phase)
        // Le filtre reste alimenté pendant la pause, mais rien n'est cumulé.
        straightTrack(10, fromM = 55.0) // 55 → 100 m
        val paused = c.state.value
        assertEquals(beforePause.distanceM, paused.distanceM, 0.0)
        assertEquals(beforePause.pointCount, paused.pointCount)
        assertEquals(beforePause.elapsedSec, paused.elapsedSec)

        c.resume()
        runCurrent()
        assertEquals(OutingPhase.ACTIVE, c.state.value.phase)
        straightTrack(10, fromM = 105.0) // 105 → 150 m
        val resumed = c.state.value
        val gained = resumed.distanceM - paused.distanceM
        // 10 fixes de 5 m : pas de rattrapage des 50 m parcourus en pause.
        assertTrue("gain $gained", gained in 30.0..60.0)
        assertEquals(paused.pointCount + 10, resumed.pointCount)
        assertEquals(paused.elapsedSec + 10, resumed.elapsedSec)
    }

    @Test
    fun `le flush périodique écrit les points toutes les 20 s et est idempotent`() = runTest {
        val c = controller()
        val id = start(c)
        straightTrack(5) // 5 s écoulées

        assertTrue(repos.sessions.getTrackPoints(id).isEmpty())
        advanceTimeBy(TrackingController.FLUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(5, repos.sessions.getTrackPoints(id).size)

        // Rien de neuf : le tick suivant n'écrit rien de plus.
        advanceTimeBy(TrackingController.FLUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(5, repos.sessions.getTrackPoints(id).size)

        straightTrack(2, fromM = 25.0)
        advanceTimeBy(TrackingController.FLUSH_INTERVAL_MS)
        runCurrent()
        assertEquals(7, repos.sessions.getTrackPoints(id).size)
        assertTrue(c.takeUnflushed().isEmpty())
    }

    @Test
    fun `la pause flushe immédiatement`() = runTest {
        val c = controller()
        val id = start(c)
        straightTrack(3)

        c.pause()
        runCurrent()

        assertEquals(3, repos.sessions.getTrackPoints(id).size)
    }

    @Test
    fun `finish finalise la séance avec temps en mouvement, vitesse moyenne, calories et FC`() = runTest {
        val c = controller()
        val id = start(c)
        for (i in 0 until 21) {
            hr.flow.emit(HrFrame(now(), 140 + (i % 2)))
            csc.flow.emit(CscFrame(now(), cadenceRpm = 80, speedKmh = 18.5))
            runCurrent()
            fix(i * STEP_M)
        }
        assertEquals(140, c.state.value.bpm)
        assertEquals(80, c.state.value.cadenceRpm)
        assertEquals(18.5, c.state.value.wheelSpeedKmh!!, 0.0)

        val saved = c.finish()

        assertEquals(id, saved)
        val s = c.state.value
        assertEquals(OutingPhase.IDLE, s.phase)
        assertEquals(id, s.savedSessionId)
        assertNull(s.sessionId)
        assertEquals(GpsStatus.IDLE, s.gpsStatus)

        val row = repos.sessions.getSession(id)!!
        assertNotNull(row.endedAt)
        assertEquals(now(), row.endedAt)
        assertEquals(21, row.durationSec)
        // 20 segments d'une seconde à 18 km/h : tous « en mouvement ».
        assertEquals(20, row.movingTimeSec)
        val distance = row.distanceM!!
        assertTrue("distance $distance", distance in 80.0..105.0)
        assertEquals(distance / 1000 / (20 / 3600.0), row.avgSpeedKmh!!, 0.001)
        assertEquals(18.0, row.maxSpeedKmh!!, 0.01)
        assertTrue("calories ${row.calories}", row.calories!! > 0)
        assertEquals(140.0, row.avgHr!!, 0.0) // 11 × 140 + 10 × 141 → 140,5 arrondi
        assertEquals(141.0, row.maxHr!!, 0.0)
        assertEquals(80.0, row.avgCadence!!, 0.0)
        assertEquals(80.0, row.maxCadence!!, 0.0)

        val points = repos.sessions.getTrackPoints(id)
        assertEquals(21, points.size)
        assertTrue(points.all { it.hr != null && it.cadence == 80.0 })
        assertTrue(repos.sessions.listInProgressSessions().isEmpty())
    }

    @Test
    fun `un échec d'écriture passe en save-failed et le réessai réutilise le même id et le même tracé`() = runTest {
        val failing = spyk(repos.sessions)
        coEvery { failing.finalizeSession(any(), any(), any()) } throws IllegalStateException("disque plein") andThenAnswer {
            callOriginal()
        }
        val c = controller(sessions = failing)
        val id = start(c)
        straightTrack(11)
        val frozenDistance = c.state.value.distanceM

        assertNull(c.finish())
        assertEquals(OutingPhase.SAVE_FAILED, c.state.value.phase)
        assertEquals(TrackingController.ERROR_SAVE, c.state.value.error)
        assertEquals(id, c.state.value.sessionId)
        assertNull(repos.sessions.getSession(id)!!.endedAt)

        // Le GPS est coupé : un fix tardif ne change plus rien.
        gps.flow.emit(fixAt(now(), 500.0))
        runCurrent()
        advanceTimeBy(5_000)

        assertEquals(id, c.retrySave())
        assertEquals(OutingPhase.IDLE, c.state.value.phase)
        val row = repos.sessions.getSession(id)!!
        assertNotNull(row.endedAt)
        assertEquals(frozenDistance, row.distanceM!!, 0.0)
        assertEquals(11, row.durationSec) // chrono figé au premier « Terminer »
        assertEquals(11, repos.sessions.getTrackPoints(id).size)
    }

    @Test
    fun `discard supprime la séance en cours`() = runTest {
        val c = controller()
        val id = start(c)
        straightTrack(3)

        c.discard()

        assertEquals(OutingPhase.IDLE, c.state.value.phase)
        assertNull(c.state.value.sessionId)
        assertNull(repos.sessions.getSession(id))
        assertEquals(0, c.state.value.pointCount)
        assertEquals(0, c.state.value.elapsedSec)
    }

    @Test
    fun `le tracé live est décimé à 8 m, départ conservé`() = runTest {
        val c = controller()
        start(c)

        straightTrack(21) // 21 points de 5 m en 5 m

        val s = c.state.value
        val path = s.livePath
        assertTrue("taille ${path.size}", path.size in 8..12)
        assertTrue(path.size < s.pointCount)
        assertEquals(48.8566, path.first().lat, 1e-9)
        // Les ancres consécutives sont toujours à ≥ 8 m les unes des autres.
        for (i in 1 until path.size - 1) {
            val d = ovh.battistella.elan.domain.haversineMeters(path[i - 1], path[i])
            assertTrue("écart $d", d >= TrackingController.LIVE_DECIMATE_M)
        }
    }

    @Test
    fun `les trames capteurs hors phase active ne sont pas échantillonnées`() = runTest {
        val c = controller()
        hr.flow.emit(HrFrame(now(), 95))
        runCurrent()
        assertEquals(95, c.state.value.bpm)

        val id = start(c)
        straightTrack(3)
        c.pause()
        runCurrent()
        hr.flow.emit(HrFrame(now(), 150))
        runCurrent()
        c.resume()
        runCurrent()
        straightTrack(2, fromM = 15.0)

        c.finish()
        // La trame reçue en pause n'a pas été retenue : la moyenne ne la voit pas.
        assertNull(repos.sessions.getSession(id)!!.avgHr)
    }

    private companion object {
        const val BASE_MS = 1_700_000_000_000L
        const val STEP_M = 5.0
    }
}
