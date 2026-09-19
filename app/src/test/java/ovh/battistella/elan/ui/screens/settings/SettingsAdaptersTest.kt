// Adaptateurs des ports Réglages : traduction des types et, pour Health
// Connect, pilotage de la demande de permissions du gestionnaire.
package ovh.battistella.elan.ui.screens.settings

import androidx.activity.result.contract.ActivityResultContract
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import ovh.battistella.elan.health.HealthConnectGateway
import ovh.battistella.elan.health.HealthConnectManager
import ovh.battistella.elan.health.HealthRecordSpec
import ovh.battistella.elan.health.HealthRecordType
import ovh.battistella.elan.health.HealthSdkStatus
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.data.export.ImportReport as ExportImportReport

private class StubGateway : HealthConnectGateway {
    var status = HealthSdkStatus.AVAILABLE
    var granted: Set<String> = emptySet()
    override fun sdkStatus() = status
    override suspend fun grantedPermissions() = granted
    override fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> = mockk(relaxed = true)
    override suspend fun insert(spec: HealthRecordSpec) = Unit
    override suspend fun deleteByClientIds(type: HealthRecordType, clientIds: List<String>) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsAdaptersTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val gateway = StubGateway()
    private lateinit var port: HealthConnectManagerPort

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        port = HealthConnectManagerPort(HealthConnectManager(gateway, repos.settings), repos.settings)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `health connect - résultat complet active le réglage, partiel refuse, absent indisponible`() = runTest {
        assertEquals(HealthRecordType.WRITE_PERMISSIONS, port.permissions)
        assertFalse(port.enabled.first())

        assertEquals(HealthConnectOutcome.Granted, port.onPermissionResult(HealthRecordType.WRITE_PERMISSIONS))
        assertTrue(port.enabled.first())

        port.disable()
        assertEquals(HealthConnectOutcome.Denied, port.onPermissionResult(emptySet()))
        assertFalse(port.enabled.first())

        gateway.status = HealthSdkStatus.UNAVAILABLE
        assertEquals(HealthConnectOutcome.Unavailable, port.onPermissionResult(HealthRecordType.WRITE_PERMISSIONS))
        assertFalse(port.enabled.first())
    }

    @Test
    fun `health connect - enable sans UI n'aboutit que si tout est déjà accordé`() = runTest {
        port.enable()
        assertFalse(port.enabled.first())

        gateway.granted = HealthRecordType.WRITE_PERMISSIONS
        port.enable()
        assertTrue(port.enabled.first())
    }

    @Test
    fun `sauvegarde - décodage QR et libellés dans l'ordre des champs`() {
        val patch = parseBackupQrPatch("s3://AK:S%2FK@minio.lan/elan/backup.json")!!
        assertEquals(BackupPatch(endpoint = "https://minio.lan", bucket = "elan", objectKey = "backup.json", accessKeyId = "AK", secretAccessKey = "S/K"), patch)
        assertNull(patch.enabled)
        assertEquals("endpoint, bucket, access key, secret key, nom de l'objet", describeBackupQrPatch(patch))
        assertNull(parseBackupQrPatch("bonjour"))
        assertEquals(
            BackupPatch(enabled = true, region = "eu-west-3"),
            BackupPatch(enabled = true, region = "eu-west-3").toConfigPatch().toBackupPatch(),
        )
    }

    @Test
    fun `strava - bilan traduit champ à champ`() {
        val report = ExportImportReport(imported = 2, duplicates = 1, skipped = 3, errors = 4, details = listOf("a", "b")).toPort()
        assertEquals(ImportReport(imported = 2, duplicates = 1, skipped = 3, errors = 4, details = listOf("a", "b")), report)
    }
}
