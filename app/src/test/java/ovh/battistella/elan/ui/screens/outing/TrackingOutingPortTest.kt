package ovh.battistella.elan.ui.screens.outing

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.tracking.FakeCadence
import ovh.battistella.elan.tracking.FakeGpsSource
import ovh.battistella.elan.tracking.FakeHeartRate
import ovh.battistella.elan.tracking.OutingError
import ovh.battistella.elan.tracking.OutingState
import ovh.battistella.elan.tracking.SchedulerClock
import ovh.battistella.elan.tracking.SessionFinalizer
import ovh.battistella.elan.tracking.TrackingController
import ovh.battistella.elan.tracking.fixAt
import java.util.Optional
import ovh.battistella.elan.tracking.OutingPhase as TrackingPhase

/**
 * L'adaptateur recopie l'état du contrôleur champ pour champ et relaie les
 * commandes ; le cycle complet tourne sur un vrai contrôleur (base Room en
 * mémoire, GPS piloté par le test, temps virtuel).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingOutingPortTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val gps = FakeGpsSource()

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

    private fun TestScope.port(): TrackingOutingPort {
        val controller = TrackingController(
            context = context,
            sessions = repos.sessions,
            settings = repos.settings,
            gps = gps,
            heartRate = Optional.of(FakeHeartRate()),
            cadence = Optional.of(FakeCadence()),
            finalizer = SessionFinalizer(Optional.empty(), Optional.empty(), backgroundScope),
            clock = SchedulerClock(testScheduler, BASE_MS),
            scope = backgroundScope,
        )
        return TrackingOutingPort(controller, backgroundScope).also { runCurrent() }
    }

    private suspend fun TestScope.fix(northM: Double) {
        gps.flow.emit(fixAt(BASE_MS + testScheduler.currentTime, northM))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
    }

    @Test
    fun `projection - phases, vitesse nulle sans point, message d'erreur`() {
        val phases = TrackingPhase.entries.map { OutingState(phase = it).toUi().phase }
        assertEquals(
            listOf(OutingPhase.Idle, OutingPhase.Requesting, OutingPhase.Active, OutingPhase.Paused, OutingPhase.Saving, OutingPhase.SaveFailed),
            phases,
        )

        val idle = OutingState(type = ActivityType.MARCHE, speedKmh = 0.0, maxSpeedKmh = 0.0).toUi()
        assertEquals(ActivityType.MARCHE, idle.type)
        assertNull(idle.speedKmh)
        assertNull(idle.maxSpeedKmh)
        assertNull(idle.errorMessage)

        val live = OutingState(
            phase = TrackingPhase.ACTIVE,
            sessionId = 7,
            elapsedSec = 42,
            distanceM = 1234.5,
            speedKmh = 18.0,
            maxSpeedKmh = 25.0,
            elevationGainM = 12.0,
            pointCount = 3,
            accuracyM = 4.0,
            gpsStatus = GpsStatus.TRACKING,
            bpm = 140,
            cadenceRpm = 85,
            wheelSpeedKmh = 17.5,
            caloriesLive = 99.0,
            error = OutingError("Titre", "Corps"),
            savedSessionId = 6,
        ).toUi()
        assertEquals(OutingPhase.Active, live.phase)
        assertEquals(7L, live.sessionId)
        assertEquals(42, live.elapsedSec)
        assertEquals(1234.5, live.distanceM, 0.0)
        assertEquals(18.0, live.speedKmh)
        assertEquals(25.0, live.maxSpeedKmh)
        assertEquals(12.0, live.elevationGainM, 0.0)
        assertEquals(3, live.pointCount)
        assertEquals(4.0, live.accuracyM)
        assertEquals(GpsStatus.TRACKING, live.gpsStatus)
        assertEquals(140, live.bpm)
        assertEquals(85, live.cadenceRpm)
        assertEquals(17.5, live.wheelSpeedKmh)
        assertEquals(99.0, live.caloriesLive, 0.0)
        assertEquals("Corps", live.errorMessage)
        assertEquals(6L, live.savedSessionId)
    }

    @Test
    fun `cycle complet relayé au contrôleur - démarrer, fixes, pause, reprise, terminer`() = runTest {
        val p = port()
        assertEquals(OutingPhase.Idle, p.state.value.phase)

        p.begin(ActivityType.COURSE)
        runCurrent()
        val started = p.state.value
        assertEquals(OutingPhase.Active, started.phase)
        assertEquals(ActivityType.COURSE, started.type)
        assertNotNull(started.sessionId)
        assertNull(started.speedKmh) // aucun fix encore

        for (i in 0 until 4) fix(i * 5.0)
        val moving = p.state.value
        assertTrue(moving.distanceM > 0)
        assertTrue(moving.pointCount > 0)
        assertNotNull(moving.speedKmh)
        assertTrue(moving.elapsedSec >= 3)

        p.pause()
        runCurrent()
        assertEquals(OutingPhase.Paused, p.state.value.phase)
        p.resume()
        runCurrent()
        assertEquals(OutingPhase.Active, p.state.value.phase)

        p.finish()
        runCurrent()
        val saved = p.state.value
        assertEquals(OutingPhase.Idle, saved.phase)
        assertEquals(started.sessionId, saved.savedSessionId)
        val row = repos.sessions.getSession(saved.savedSessionId!!)!!
        assertNotNull(row.endedAt)
        assertTrue((row.distanceM ?: 0.0) > 0)
    }

    @Test
    fun `abandon supprime la séance en cours`() = runTest {
        val p = port()
        p.begin(ActivityType.VELO)
        runCurrent()
        val id = p.state.value.sessionId!!

        p.discard()
        runCurrent()
        assertEquals(OutingPhase.Idle, p.state.value.phase)
        assertNull(p.state.value.sessionId)
        assertNull(repos.sessions.getSession(id))
    }

    private companion object {
        const val BASE_MS = 1_700_000_000_000L
    }
}
