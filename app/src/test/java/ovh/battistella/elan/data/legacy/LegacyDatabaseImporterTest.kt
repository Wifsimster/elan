package ovh.battistella.elan.data.legacy

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.secrets.InMemorySecretStore
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.TestSupport
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
class LegacyDatabaseImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val paths = LegacyPaths(context)

    private lateinit var db: ElanDatabase
    private lateinit var settings: SettingsRepository
    private val secrets = InMemorySecretStore()
    private var freeBytes = Long.MAX_VALUE
    private val now = 1_726_000_000_000L
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    /** Clé AES en mémoire jouant le rôle de l'AndroidKeyStore d'expo-secure-store. */
    private val legacyKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val securePrefs = context.getSharedPreferences(LegacyPaths.SECURE_STORE_PREFS, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        settings = TestSupport.repositories(db).settings
        securePrefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        paths.sqliteDir.deleteRecursively()
        paths.migratedDir.deleteRecursively()
    }

    private fun importer(
        database: ElanDatabase = db,
        keyStore: KeyStoreAccess = KeyStoreAccess { alias ->
            if (alias == LegacySecretsReader.ALIAS_UNAUTHENTICATED) legacyKey else null
        },
    ) = LegacyDatabaseImporter(
        context = context,
        db = database,
        settings = settings,
        secretStore = secrets,
        secretsReader = LegacySecretsReader({ securePrefs }, keyStore),
        freeSpace = { freeBytes },
        io = Dispatchers.Unconfined,
        clock = clock,
    )

    private suspend fun counts() = ImportCounts(
        sessions = db.sessionDao().getAll().size.toLong(),
        trackPoints = db.trackPointDao().getAll().size.toLong(),
        muscuSets = db.muscuSetDao().getAll().size.toLong(),
        bodyMeasurements = db.bodyMeasurementDao().getAll().size.toLong(),
        settings = db.settingsDao().getAll().size.toLong(),
    )

    private fun writeSecureStoreEntry(name: String, plaintext: String, prefixed: Boolean = true) {
        val iv = ByteArray(12) { (it * 7 + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val json = JSONObject()
            .put("ct", Base64.encodeToString(ct, Base64.DEFAULT))
            .put("iv", Base64.encodeToString(iv, Base64.DEFAULT))
            .put("tlen", 128)
            .put("scheme", "aes")
            .put("usesKeystoreSuffix", true)
            .toString()
        securePrefs.edit().putString(if (prefixed) "${LegacySecretsReader.KEY_PREFIX}$name" else name, json).commit()
    }

    // ---- import complet ------------------------------------------------------

    @Test
    fun `le journal WAL de la fixture n'est pas fusionné`() {
        LegacyFixture.create(context)
        assertTrue(paths.dbFile.isFile)
        assertTrue("le -wal doit contenir des pages", paths.walFile.length() > 0)
    }

    @Test
    fun `import complet ids et colonnes préservés réglages copiés marqueurs posés`() = runTest {
        LegacyFixture.create(context)
        writeSecureStoreEntry(SecretStore.BACKUP_S3_ACCESS_KEY_ID, "AKIALEGACY")
        writeSecureStoreEntry(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, "s3cr3t/legacy+key=")
        val progress = mutableListOf<Pair<Long, Long>>()

        val outcome = importer().runIfNeeded { copied, total -> progress += copied to total }

        val expected = ImportCounts(
            sessions = 5,
            trackPoints = LegacyFixture.TOTAL_POINTS,
            muscuSets = LegacyFixture.MUSCU_SETS.toLong(),
            bodyMeasurements = LegacyFixture.BODY_MEASUREMENTS.toLong(),
            settings = LegacyFixture.SETTINGS.size.toLong(),
        )
        assertEquals(ImportOutcome.Imported(expected), outcome)

        // Comptages Room = comptages hérités (les réglages ont en plus les marqueurs).
        val actual = counts()
        assertEquals(expected.copy(settings = actual.settings), actual)

        // Ids et colonnes nullables préservés.
        val velo = db.sessionDao().getById(LegacyFixture.VELO_ID)!!
        assertEquals("velo", velo.type)
        assertEquals(3_402, velo.movingTimeSec)
        assertEquals(84.0, velo.avgCadence!!, 0.0)
        assertEquals("Sortie du dimanche", velo.notes)
        assertNull(velo.source)
        val strava = db.sessionDao().getById(LegacyFixture.STRAVA_ID)!!
        assertEquals("strava", strava.source)
        assertEquals(LegacyFixture.STRAVA_EXTERNAL_ID, strava.externalId)
        val inProgress = db.sessionDao().getById(LegacyFixture.IN_PROGRESS_ID)!!
        assertNull(inProgress.endedAt)
        assertEquals(LegacyFixture.IN_PROGRESS_POINTS, db.trackPointDao().getForSession(LegacyFixture.IN_PROGRESS_ID).size)
        assertNotNull(db.sessionDao().getById(LegacyFixture.ORPHAN_MUSCU_ID))
        assertEquals(0, db.muscuSetDao().getForSession(LegacyFixture.ORPHAN_MUSCU_ID).size)

        val veloPoints = db.trackPointDao().getForSession(LegacyFixture.VELO_ID)
        assertEquals(LegacyFixture.VELO_POINTS, veloPoints.size)
        assertEquals(120.0, veloPoints[0].hr!!, 0.0)
        assertEquals(80.0, veloPoints[0].cadence!!, 0.0)
        assertNull(veloPoints[9].altitude)
        assertNull(veloPoints[9].speedKmh)
        assertEquals(1_000L, veloPoints[1].ts - veloPoints[0].ts)
        assertNull(db.trackPointDao().getForSession(LegacyFixture.STRAVA_ID)[0].hr)

        val sets = db.muscuSetDao().getForSession(LegacyFixture.MUSCU_ID)
        assertEquals(LegacyFixture.MUSCU_SETS, sets.size)
        assertEquals("moyen", sets.first { it.exercise == "Développé couché" }.difficulty)
        assertNull(sets.first { it.exercise == "Élévations latérales" }.difficulty)

        assertEquals(3, db.bodyMeasurementDao().list(10).size)

        // Réglages copiés tels quels, onboarding forcé.
        for ((key, value) in LegacyFixture.SETTINGS) assertEquals(key, value, settings.getSetting(key))
        assertEquals("1", settings.getSetting(Keys.ONBOARDING_DONE))
        assertTrue(settings.settings.first().onboardingDone)

        // Secrets rapatriés depuis expo-secure-store.
        assertEquals("AKIALEGACY", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("s3cr3t/legacy+key=", secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
        assertNull(settings.getSetting(Keys.BACKUP_SECRETS_MISSING))

        // Dossier mis de côté, marqueurs posés.
        assertFalse(paths.sqliteDir.exists())
        assertTrue(File(paths.migratedDir, LegacyPaths.DB_NAME).isFile)
        assertFalse(File(paths.migratedDir, "${LegacyPaths.DB_NAME}-wal").exists())
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_DONE))
        assertEquals(now.toString(), settings.getSetting(Keys.LEGACY_IMPORT_AT))
        assertEquals(expected.toJson(), settings.getSetting(Keys.LEGACY_IMPORT_COUNTS))
        assertNull(settings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS))

        // Avancement : de 0 au total, en lots.
        assertEquals(0L to LegacyFixture.TOTAL_POINTS, progress.first())
        assertEquals(LegacyFixture.TOTAL_POINTS to LegacyFixture.TOTAL_POINTS, progress.last())
    }

    @Test
    fun `les lignes encore dans le journal WAL sont importées`() = runTest {
        LegacyFixture.create(context)
        // Le fichier principal seul ignore la dernière transaction (pesées) : on la retrouve après import.
        val walBytes = paths.walFile.length()
        assertTrue(walBytes > 0)
        val outcome = importer().runIfNeeded() as ImportOutcome.Imported
        assertEquals(LegacyFixture.BODY_MEASUREMENTS.toLong(), outcome.counts.bodyMeasurements)
        assertEquals(LegacyFixture.TOTAL_POINTS, outcome.counts.trackPoints)
        val weights = db.bodyMeasurementDao().list(10).map { it.weightKg }
        assertEquals(3, weights.size)
        listOf(77.4, 77.6, 77.8).zip(weights).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
    }

    @Test
    fun `sqlite_sequence reprend la séquence d'origine et un nouvel id ne réutilise pas un id supprimé`() = runTest {
        LegacyFixture.create(context)
        importer().runIfNeeded()
        val id = db.sessionDao().insert(TestSupport.session(type = ActivityType.MARCHE))
        assertEquals(LegacyFixture.DELETED_ID + 1, id)
    }

    @Test
    fun `une seconde passe ne fait rien`() = runTest {
        LegacyFixture.create(context)
        assertTrue(importer().runIfNeeded() is ImportOutcome.Imported)
        val before = counts()
        assertEquals(ImportOutcome.NothingToDo, importer().runIfNeeded())
        assertEquals(before, counts())
    }

    @Test
    fun `sans base héritée rien à faire et marqueur posé`() = runTest {
        assertEquals(ImportOutcome.NothingToDo, importer().runIfNeeded())
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_DONE))
        assertNull(settings.getSetting(Keys.ONBOARDING_DONE))
        assertFalse(paths.migratedDir.exists())
    }

    @Test
    fun `un fichier hérité vide de tout schéma est mis de côté sans rien copier`() = runTest {
        paths.sqliteDir.mkdirs()
        paths.dbFile.writeBytes(ByteArray(0))
        assertEquals(ImportOutcome.NothingToDo, importer().runIfNeeded())
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_DONE))
        assertNull(settings.getSetting(Keys.ONBOARDING_DONE))
        assertFalse(paths.sqliteDir.exists())
        assertTrue(File(paths.migratedDir, LegacyPaths.DB_NAME).isFile)
    }

    @Test
    fun `une base en version 5 reçoit les colonnes manquantes et s'importe`() = runTest {
        LegacyFixture.create(context, version = 5)
        val outcome = importer().runIfNeeded()
        assertTrue(outcome.toString(), outcome is ImportOutcome.Imported)
        val sets = db.muscuSetDao().getForSession(LegacyFixture.MUSCU_ID)
        assertEquals(LegacyFixture.MUSCU_SETS, sets.size)
        assertTrue(sets.all { it.difficulty == null })
        assertEquals(3_402, db.sessionDao().getById(LegacyFixture.VELO_ID)!!.movingTimeSec)
        // La colonne a bien été ajoutée à la base héritée (mise de côté).
        LegacyFixture.open(File(paths.migratedDir, LegacyPaths.DB_NAME)).use { legacy ->
            legacy.rawQuery("PRAGMA table_info(muscu_sets)", null).use { c ->
                val names = buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                assertTrue("difficulty" in names)
            }
            assertEquals(5, legacy.version)
        }
    }

    @Test
    fun `une base en version 1 s'importe sans journal de poids`() = runTest {
        LegacyFixture.create(context, version = 1)
        val outcome = importer().runIfNeeded() as ImportOutcome.Imported
        assertEquals(0L, outcome.counts.bodyMeasurements)
        assertEquals(5L, outcome.counts.sessions)
        assertEquals(LegacyFixture.TOTAL_POINTS, outcome.counts.trackPoints)
        val velo = db.sessionDao().getById(LegacyFixture.VELO_ID)!!
        assertNull(velo.movingTimeSec)
        assertNull(velo.avgCadence)
        assertNull(db.trackPointDao().getForSession(LegacyFixture.VELO_ID)[0].cadence)
    }

    // ---- échecs --------------------------------------------------------------

    /** Base Room sur fichier (`elan.db`), comme en production. */
    private fun fileDb(): ElanDatabase = Room.databaseBuilder(context, ElanDatabase::class.java, ElanDatabase.NAME)
        .allowMainThreadQueries()
        .setQueryExecutor(Executor { it.run() })
        .setTransactionExecutor(Executor { it.run() })
        .build()

    @Test
    fun `un échec en pleine copie laisse la base héritée intacte et la base Room annulée`() = runTest {
        LegacyFixture.create(context, orphanTrackPoint = true)
        val fileDb = fileDb()
        val fileSettings = SettingsRepository(fileDb.settingsDao(), Dispatchers.Unconfined)
        fileSettings.setSetting("temoin", "1")
        settings = fileSettings

        try {
            val outcome = importer(database = fileDb).runIfNeeded()

            assertTrue(outcome.toString(), outcome is ImportOutcome.Failed)
            outcome as ImportOutcome.Failed
            assertEquals(1, outcome.attempt)
            assertTrue(outcome.retriable)
            assertTrue(outcome.reason, outcome.reason.contains("FOREIGN KEY", ignoreCase = true))

            // Base héritée intacte, toujours en place (journal fusionné, contenu conservé).
            assertTrue(paths.dbFile.isFile)
            assertFalse(paths.migratedDir.exists())
            LegacyFixture.open(paths.dbFile).use { legacy ->
                legacy.rawQuery("SELECT COUNT(*) FROM track_points", null).use { c ->
                    c.moveToFirst()
                    assertEquals(LegacyFixture.TOTAL_POINTS + 1, c.getLong(0))
                }
            }

            // Transaction annulée : aucune ligne copiée, la ligne témoin est toujours là, l'échec est noté.
            assertEquals("1", fileSettings.getSetting("temoin"))
            assertEquals("1", fileSettings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS))
            assertNotNull(fileSettings.getSetting(Keys.LEGACY_IMPORT_ERROR))
            assertNull(fileSettings.getSetting(Keys.LEGACY_IMPORT_DONE))
            assertNull(fileSettings.getSetting(Keys.ONBOARDING_DONE))
            assertEquals(0, fileDb.sessionDao().getAll().size)
            assertEquals(0, fileDb.trackPointDao().getAll().size)
            assertEquals(0, fileDb.muscuSetDao().getAll().size)
            assertEquals(0, fileDb.bodyMeasurementDao().getAll().size)
            assertTrue(context.getDatabasePath(ElanDatabase.NAME).isFile)

            // Deuxième tentative sur la même base : le compteur avance, rien d'autre ne change.
            val again = importer(database = fileDb).runIfNeeded() as ImportOutcome.Failed
            assertEquals(2, again.attempt)
            assertEquals("2", fileSettings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS))
            assertEquals(0, fileDb.sessionDao().getAll().size)
        } finally {
            fileDb.close()
            context.deleteDatabase(ElanDatabase.NAME)
        }
    }

    @Test
    fun `après un échec la base Room reste observable`() = runTest {
        LegacyFixture.create(context, orphanTrackPoint = true)
        val fileDb = fileDb()
        val fileSettings = SettingsRepository(fileDb.settingsDao(), Dispatchers.Unconfined)
        settings = fileSettings
        try {
            assertTrue(importer(database = fileDb).runIfNeeded() is ImportOutcome.Failed)

            // Le Flow des réglages doit encore réagir aux écritures (suivi d'invalidation intact).
            val seen = mutableListOf<Int?>()
            val job = launch(Dispatchers.Unconfined) {
                fileSettings.settings.collect { seen += it.restSeconds }
            }
            fileSettings.setRestSeconds(120)
            job.cancel()
            assertTrue("le flux doit voir la mise à jour : $seen", 120 in seen)
        } finally {
            fileDb.close()
            context.deleteDatabase(ElanDatabase.NAME)
        }
    }

    @Test
    fun `après un échec une base héritée réparée s'importe sur la même base Room`() = runTest {
        LegacyFixture.create(context, orphanTrackPoint = true)
        assertTrue(importer().runIfNeeded() is ImportOutcome.Failed)
        // L'utilisateur (ou un correctif) répare la base héritée : le point orphelin disparaît.
        LegacyFixture.open(paths.dbFile).use { it.execSQL("DELETE FROM track_points WHERE sessionId = 4242") }

        val outcome = importer().runIfNeeded()
        assertTrue(outcome.toString(), outcome is ImportOutcome.Imported)
        assertEquals(LegacyFixture.TOTAL_POINTS, db.trackPointDao().getAll().size.toLong())
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_DONE))
        assertNull(settings.getSetting(Keys.LEGACY_IMPORT_ERROR))
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS))
    }

    @Test
    fun `sans espace disque suffisant l'import est refusé avant toute lecture`() = runTest {
        LegacyFixture.create(context)
        freeBytes = paths.legacyBytes // il en faut strictement plus du double
        val outcome = importer().runIfNeeded() as ImportOutcome.Failed
        assertTrue(outcome.reason, outcome.reason.contains("espace disque"))
        assertTrue(outcome.retriable)
        assertEquals(1, outcome.attempt)
        assertTrue(paths.dbFile.isFile)
        assertTrue("le journal n'a pas été touché", paths.walFile.length() > 0)
        assertEquals(0, db.sessionDao().getAll().size)
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS))

        freeBytes = Long.MAX_VALUE
        assertTrue(importer().runIfNeeded() is ImportOutcome.Imported)
    }

    @Test
    fun `une base plus récente que la version 7 est refusée sans nouvelle tentative`() = runTest {
        LegacyFixture.create(context)
        LegacyFixture.open(paths.dbFile).use { it.version = 8 }
        val outcome = importer().runIfNeeded() as ImportOutcome.Failed
        assertFalse(outcome.retriable)
        assertTrue(outcome.reason, outcome.reason.contains("version 8"))
        assertTrue(paths.dbFile.isFile)
        assertEquals(0, db.sessionDao().getAll().size)
    }

    // ---- secrets -------------------------------------------------------------

    @Test
    fun `sans secrets lisibles et avec une config S3 le manque est signalé`() = runTest {
        LegacyFixture.create(context)
        // Entrées présentes mais clé du KeyStore perdue.
        writeSecureStoreEntry(SecretStore.BACKUP_S3_ACCESS_KEY_ID, "AKIALEGACY")
        val outcome = importer(keyStore = KeyStoreAccess { null }).runIfNeeded()
        assertTrue(outcome is ImportOutcome.Imported)
        assertNull(secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("1", settings.getSetting(Keys.BACKUP_SECRETS_MISSING))
    }

    @Test
    fun `sans config S3 l'absence de secrets n'est pas signalée`() = runTest {
        LegacyFixture.create(context, settings = LegacyFixture.SETTINGS - "backup_s3")
        assertTrue(importer().runIfNeeded() is ImportOutcome.Imported)
        assertNull(settings.getSetting(Keys.BACKUP_SECRETS_MISSING))
    }

    @Test
    fun `une entrée secure-store sans préfixe est lue aussi`() = runTest {
        LegacyFixture.create(context)
        writeSecureStoreEntry(SecretStore.BACKUP_S3_ACCESS_KEY_ID, "AK-nu", prefixed = false)
        writeSecureStoreEntry(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, "SK-nu", prefixed = false)
        assertTrue(importer().runIfNeeded() is ImportOutcome.Imported)
        assertEquals("AK-nu", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("SK-nu", secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
    }

    @Test
    fun `une config héritée avec secrets dans le JSON est déplacée vers le coffre`() = runTest {
        val legacyConfig = """{"enabled":true,"endpoint":"https://s3.example.org","region":"","bucket":"elan","objectKey":"","accessKeyId":"AKIAJSON","secretAccessKey":"SKJSON"}"""
        LegacyFixture.create(context, settings = LegacyFixture.SETTINGS + ("backup_s3" to legacyConfig))
        assertTrue(importer().runIfNeeded() is ImportOutcome.Imported)

        assertEquals("AKIAJSON", secrets.get(SecretStore.BACKUP_S3_ACCESS_KEY_ID))
        assertEquals("SKJSON", secrets.get(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY))
        val rewritten = JSONObject(settings.getSetting(Keys.BACKUP_S3)!!)
        assertFalse(rewritten.has("accessKeyId"))
        assertFalse(rewritten.has("secretAccessKey"))
        assertEquals("https://s3.example.org", rewritten.getString("endpoint"))
        assertEquals("elan", rewritten.getString("bucket"))
        assertTrue(rewritten.getBoolean("enabled"))
        assertNull(settings.getSetting(Keys.BACKUP_SECRETS_MISSING))
    }

    // ---- abandon et purge ----------------------------------------------------

    @Test
    fun `markSkipped met la base de côté sans la lire`() = runTest {
        LegacyFixture.create(context)
        importer().markSkipped()
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_DONE))
        assertEquals("1", settings.getSetting(Keys.LEGACY_IMPORT_SKIPPED))
        assertNull(settings.getSetting(Keys.ONBOARDING_DONE))
        assertFalse(paths.sqliteDir.exists())
        assertTrue(File(paths.migratedDir, LegacyPaths.DB_NAME).isFile)
        // Sans lecture, le journal non fusionné part avec le dossier : rien n'est perdu dans le filet.
        assertTrue(File(paths.migratedDir, "${LegacyPaths.DB_NAME}-wal").length() > 0)
        assertEquals(0, db.sessionDao().getAll().size)
        assertEquals(ImportOutcome.NothingToDo, importer().runIfNeeded())
    }

    @Test
    fun `cleanupIfDue purge les restes après 30 jours seulement`() = runTest {
        LegacyFixture.create(context)
        val prefsDir = paths.sharedPrefsDir.apply { mkdirs() }
        val leftovers = listOf(
            LegacyPaths.SECURE_STORE_PREFS_FILE,
            LegacyPaths.TASK_MANAGER_PREFS_FILE,
            "expo.modules.notifications.SharedPreferences.xml",
        ).map { File(prefsDir, it).apply { writeText("<map/>") } }
        val other = File(prefsDir, "autre.xml").apply { writeText("<map/>") }
        importer().runIfNeeded()

        val imp = importer()
        imp.cleanupIfDue(now + 29L * 86_400_000L)
        assertTrue(paths.migratedDir.exists())
        assertTrue(leftovers.all { it.exists() })
        assertNull(settings.getSetting(Keys.LEGACY_CLEANUP_DONE))

        imp.cleanupIfDue(now + 31L * 86_400_000L)
        assertFalse(paths.migratedDir.exists())
        assertTrue(leftovers.none { it.exists() })
        assertTrue(other.exists())
        assertEquals("1", settings.getSetting(Keys.LEGACY_CLEANUP_DONE))
    }
}
