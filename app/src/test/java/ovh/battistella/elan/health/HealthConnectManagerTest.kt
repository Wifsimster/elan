package ovh.battistella.elan.health

import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.HrSample
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.tracking.SavedSessionData
import ovh.battistella.elan.tracking.SessionFinalizer
import java.util.Optional

/** Passerelle factice : journal des opérations, statut et permissions pilotables. */
private class FakeGateway : HealthConnectGateway {
    var status = HealthSdkStatus.AVAILABLE
    var granted: Set<String> = HealthRecordType.WRITE_PERMISSIONS
    var failInsertOf: HealthRecordType? = null
    val ops = mutableListOf<String>()

    override fun sdkStatus() = status
    override suspend fun grantedPermissions() = granted
    override fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> = mockk(relaxed = true)

    override suspend fun insert(spec: HealthRecordSpec) {
        if (spec.type == failInsertOf) throw IllegalStateException("insert refusé")
        ops += "insert:${spec.type}:${spec.clientRecordId}"
    }

    override suspend fun deleteByClientIds(type: HealthRecordType, clientIds: List<String>) {
        ops += "delete:$type:${clientIds.joinToString()}"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthConnectManagerTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val gateway = FakeGateway()
    private lateinit var manager: HealthConnectManager

    private val t0 = 1_780_308_000_000L
    private val t1 = t0 + 3_600_000L
    private val velo = HealthSessionData(ActivityType.VELO, t0, t1, distanceM = 25_000.0, calories = 600.0, hrSamples = listOf(HrSample(t0 + 1000, 120.0)))

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        manager = HealthConnectManager(gateway, repos.settings)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun optIn() = repos.settings.setHealthConnect(true)

    @Test
    fun `opt-in désactivé - aucun export`() = runTest {
        manager.exportSession(velo)
        assertTrue(gateway.ops.isEmpty())
    }

    @Test
    fun `SDK indisponible - aucun export ni suppression`() = runTest {
        optIn()
        gateway.status = HealthSdkStatus.UPDATE_REQUIRED
        manager.exportSession(velo)
        manager.removeSession(ActivityType.VELO, t0)
        assertTrue(gateway.ops.isEmpty())
    }

    @Test
    fun `permission de session révoquée - aucun export`() = runTest {
        optIn()
        gateway.granted = setOf(HealthRecordType.DISTANCE.writePermission)
        manager.exportSession(velo)
        assertTrue(gateway.ops.isEmpty())
    }

    @Test
    fun `permission partielle - seuls les types autorisés sont écrits`() = runTest {
        optIn()
        gateway.granted = setOf(HealthRecordType.EXERCISE_SESSION.writePermission, HealthRecordType.HEART_RATE.writePermission)
        manager.exportSession(velo)
        assertEquals(listOf("insert:EXERCISE_SESSION:elan-velo-$t0-session", "insert:HEART_RATE:elan-velo-$t0-hr"), gateway.ops)
    }

    @Test
    fun `un insert par enregistrement, chacun best-effort`() = runTest {
        optIn()
        gateway.failInsertOf = HealthRecordType.DISTANCE
        manager.exportSession(velo)
        assertEquals(
            listOf(
                "insert:EXERCISE_SESSION:elan-velo-$t0-session",
                "insert:ACTIVE_CALORIES:elan-velo-$t0-calories",
                "insert:HEART_RATE:elan-velo-$t0-hr",
            ),
            gateway.ops,
        )
    }

    @Test
    fun `suppression par identifiant client pour les quatre types`() = runTest {
        optIn()
        manager.removeSession(ActivityType.COURSE, t0)
        assertEquals(
            listOf(
                "delete:EXERCISE_SESSION:elan-course-$t0-session",
                "delete:DISTANCE:elan-course-$t0-distance",
                "delete:ACTIVE_CALORIES:elan-course-$t0-calories",
                "delete:HEART_RATE:elan-course-$t0-hr",
            ),
            gateway.ops,
        )
    }

    @Test
    fun `retype via SessionFinalizer - ancien miroir supprimé PUIS nouveau écrit`() = runTest {
        optIn()
        val finalizer = SessionFinalizer(Optional.empty(), Optional.of(manager), CoroutineScope(Dispatchers.Unconfined))
        finalizer.onRetyped(ActivityType.VELO, SavedSessionData(ActivityType.COURSE, t0, t1, distanceM = 8000.0, calories = 500.0))

        assertEquals(
            listOf(
                "delete:EXERCISE_SESSION:elan-velo-$t0-session",
                "delete:DISTANCE:elan-velo-$t0-distance",
                "delete:ACTIVE_CALORIES:elan-velo-$t0-calories",
                "delete:HEART_RATE:elan-velo-$t0-hr",
                "insert:EXERCISE_SESSION:elan-course-$t0-session",
                "insert:DISTANCE:elan-course-$t0-distance",
                "insert:ACTIVE_CALORIES:elan-course-$t0-calories",
            ),
            gateway.ops,
        )
    }

    @Test
    fun `enable - SDK indisponible`() = runTest {
        gateway.status = HealthSdkStatus.UNAVAILABLE
        assertEquals(HealthEnableResult.UNAVAILABLE, manager.enable())
        assertFalse(manager.isSupported)
        assertFalse(repos.settings.snapshot().healthConnect)
    }

    @Test
    fun `enable - permissions déjà accordées, opt-in mémorisé sans passer par le système`() = runTest {
        assertEquals(HealthEnableResult.GRANTED, manager.enable())
        assertTrue(repos.settings.snapshot().healthConnect)
        assertTrue(manager.isSupported)
    }

    @Test
    fun `enable - demande système puis refus partiel = DENIED, opt-in non mémorisé`() = runTest {
        gateway.granted = emptySet()
        // L'écran s'abonne (LaunchedEffect) avant de déclencher l'activation.
        val requested = async { manager.permissionRequests.first() }
        val result = async { manager.enable() }
        advanceUntilIdle()
        assertEquals(HealthRecordType.WRITE_PERMISSIONS, requested.await())

        manager.onPermissionResult(setOf(HealthRecordType.EXERCISE_SESSION.writePermission))
        assertEquals(HealthEnableResult.DENIED, result.await())
        assertFalse(repos.settings.snapshot().healthConnect)
    }

    @Test
    fun `enable - demande système acceptée = GRANTED puis disable`() = runTest {
        gateway.granted = emptySet()
        val requested = async { manager.permissionRequests.first() }
        val result = async { manager.enable() }
        advanceUntilIdle()
        requested.await()
        manager.onPermissionResult(HealthRecordType.WRITE_PERMISSIONS)
        assertEquals(HealthEnableResult.GRANTED, result.await())
        assertTrue(manager.isEnabled())

        manager.disable()
        advanceUntilIdle()
        assertFalse(manager.isEnabled())
    }

    @Test
    fun `le contrat de permission est celui de la passerelle`() {
        // Pas de résolution système sous JVM : on vérifie seulement le câblage.
        assertTrue(manager.permissionContract() !is ActivityResultContracts.RequestPermission)
    }
}
