package ovh.battistella.elan.data.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.strava.GpxTcxParserTest
import ovh.battistella.elan.domain.strava.StravaImport
import ovh.battistella.elan.testing.TestSupport
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class StravaImporterTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private lateinit var importer: StravaImporter
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.cacheDir, "import-test").apply { mkdirs() }

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
        importer = StravaImporter(context, repos.sessions, repos.settings, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(dir, name).apply { writeBytes(bytes) })
    private fun file(name: String, text: String): Uri = file(name, text.toByteArray())

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    @Test
    fun `importe un GPX, un TCX gzippé, et compte les doublons`() = runTest {
        repos.settings.saveProfile(Profile(weightKg = 80.0))
        val gpx = file("sortie.gpx", GpxTcxParserTest.GPX_SAMPLE)
        val tcxGz = file("course.tcx.gz", gzip(GpxTcxParserTest.TCX_SAMPLE.toByteArray()))

        val r1 = importer.importUris(listOf(gpx, tcxGz))
        assertEquals(ImportReport(imported = 2, duplicates = 0, skipped = 0, errors = 0, details = emptyList()), r1)
        val sessions = db.sessionDao().getAll()
        assertEquals(2, sessions.size)
        assertTrue(sessions.all { it.source == "strava" && it.externalId!!.startsWith("strava-") })
        // 3 points pour le GPX, 2 pour le TCX.
        assertEquals(listOf(2, 3), db.trackPointDao().getAll().groupBy { it.sessionId }.values.map { it.size }.sorted())

        // Ré-import du même fichier : doublon, rien d'écrit.
        val r2 = importer.importUris(listOf(gpx))
        assertEquals(ImportReport(0, 1, 0, 0, emptyList()), r2)
        assertEquals(2, db.sessionDao().getAll().size)
    }

    @Test
    fun `une erreur sur un fichier n'interrompt pas les autres`() = runTest {
        val bad = file("bidule.txt", "<html></html>")
        val doctype = file("xxe.gpx", "<!DOCTYPE x><gpx></gpx>")
        val natation = file("natation.tcx", GpxTcxParserTest.TCX_SAMPLE.replace("Sport=\"Biking\"", "Sport=\"Swimming\""))
        val absent = Uri.fromFile(File(dir, "absent.gpx"))
        val ok = file("ok.gpx", GpxTcxParserTest.GPX_SAMPLE)

        val r = importer.importUris(listOf(bad, doctype, natation, absent, ok))

        assertEquals(1, r.imported)
        assertEquals(0, r.duplicates)
        assertEquals(1, r.skipped)
        assertEquals(3, r.errors)
        assertEquals(4, r.details.size)
        assertEquals("bidule.txt : Format non reconnu (ni GPX ni TCX).", r.details[0])
        assertTrue(r.details[1].startsWith("xxe.gpx : Fichier refusé"))
        assertEquals("natation.tcx : ${StravaImport.SKIPPED_UNSUPPORTED_TYPE}", r.details[2])
        assertTrue(r.details[3].startsWith("absent.gpx : "))
    }

    @Test
    fun `un fichier au-delà de 30 Mo est refusé sans être décodé`() = runTest {
        val huge = file("huge.gpx", ByteArray((StravaImporter.MAX_BYTES + 1).toInt()))
        val r = importer.importUris(listOf(huge))
        assertEquals(ImportReport(0, 0, 0, 1, listOf("huge.gpx : fichier trop volumineux")), r)
        assertTrue(db.sessionDao().getAll().isEmpty())
    }

    @Test
    fun `liste vide → bilan vide`() = runTest {
        assertEquals(ImportReport(0, 0, 0, 0, emptyList()), importer.importUris(emptyList()))
    }
}
