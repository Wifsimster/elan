package ovh.battistella.elan.tracking

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
import ovh.battistella.elan.testing.TestSupport

/** Transposition de `__tests__/lib/session-recovery.test.ts` sur la vraie base. */
@RunWith(RobolectricTestRunner::class)
class SessionRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val controllerState = MutableStateFlow(OutingState())
    private val controller = mockk<TrackingController>().also {
        every { it.state } returns controllerState
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

    private fun recovery() = SessionRecovery(repos.sessions, controller)

    @Test
    fun `une sortie orpheline avec des points est finalisée depuis ses points, avec la note`() = runTest {
        val start = 1_700_000_000_000L
        val id = repos.sessions.createSession(ActivityType.VELO, start)
        repos.sessions.insertTrackPoints(
            id,
            (0 until 10).map { i ->
                TestSupport.trackPointInput(
                    ts = start + i * 1000L,
                    lat = 48.8566 + i * 5 * DEG_PER_METER_LAT,
                    speedKmh = 18.0,
                    hr = 130.0,
                )
            },
        )

        val outcome = recovery().recoverOrphans()

        assertEquals(RecoveryOutcome(recovered = 1, purged = 0), outcome)
        val row = repos.sessions.getSession(id)!!
        assertEquals(start + 9000, row.endedAt)
        assertEquals(9, row.durationSec)
        assertEquals(9, row.movingTimeSec)
        assertEquals(SessionRecovery.RECOVERED_NOTE, row.notes)
        assertNotNull(row.distanceM)
        assertNotNull(row.avgSpeedKmh)
        assertEquals(130.0, row.avgHr!!, 0.0)
        assertEquals(10, repos.sessions.getTrackPoints(id).size)
        assertTrue(repos.sessions.listInProgressSessions().isEmpty())
    }

    @Test
    fun `une sortie orpheline sans point et une muscu orpheline sont purgées`() = runTest {
        val empty = repos.sessions.createSession(ActivityType.COURSE, 1_700_000_000_000L)
        val single = repos.sessions.createSession(ActivityType.MARCHE, 1_700_000_100_000L)
        repos.sessions.insertTrackPoints(single, listOf(TestSupport.trackPointInput()))
        val muscu = repos.sessions.createSession(ActivityType.MUSCU, 1_700_000_200_000L)

        val outcome = recovery().recoverOrphans()

        assertEquals(RecoveryOutcome(recovered = 0, purged = 3), outcome)
        assertNull(repos.sessions.getSession(empty))
        assertNull(repos.sessions.getSession(single))
        assertNull(repos.sessions.getSession(muscu))
    }

    @Test
    fun `les séances terminées ne sont pas touchées`() = runTest {
        db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, notes = "ok"))

        val outcome = recovery().recoverOrphans()

        assertEquals(RecoveryOutcome(0, 0), outcome)
        assertEquals("ok", repos.sessions.listSessions().single().notes)
    }

    @Test
    fun `le service orphelin est arrêté seulement sans sortie en cours`() {
        val app = context as Application
        controllerState.value = OutingState(phase = OutingPhase.ACTIVE)
        recovery().reconcileOrphanService(context)
        assertNull(shadowOf(app).nextStoppedService)

        controllerState.value = OutingState()
        recovery().reconcileOrphanService(context)
        assertEquals(TrackingService::class.java.name, shadowOf(app).nextStoppedService.component?.className)
    }
}
