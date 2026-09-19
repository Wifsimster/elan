package ovh.battistella.elan.data.legacy

import android.content.Context
import android.os.StatFs
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ovh.battistella.elan.data.local.BodyMeasurementEntity
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.local.MuscuSetEntity
import ovh.battistella.elan.data.local.SessionEntity
import ovh.battistella.elan.data.local.SettingEntity
import ovh.battistella.elan.data.secrets.SecretStore
import ovh.battistella.elan.data.settings.SettingsJson
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.data.settings.SettingsRepository.Keys
import java.io.File
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une passe d'import. */
sealed interface ImportOutcome {
    /** Rien à importer (déjà fait, ou aucune base héritée). */
    data object NothingToDo : ImportOutcome

    data class Imported(val counts: ImportCounts) : ImportOutcome

    /**
     * Échec ; [retriable] est faux quand rejouer ne changera rien (base plus
     * récente que ce que l'app sait lire, fichier corrompu).
     */
    data class Failed(val reason: String, val attempt: Int, val retriable: Boolean) : ImportOutcome
}

/** Lignes copiées par table. */
data class ImportCounts(
    val sessions: Long,
    val trackPoints: Long,
    val muscuSets: Long,
    val bodyMeasurements: Long,
    val settings: Long,
) {
    fun toJson(): String = JSONObject()
        .put("sessions", sessions)
        .put("trackPoints", trackPoints)
        .put("muscuSets", muscuSets)
        .put("bodyMeasurements", bodyMeasurements)
        .put("settings", settings)
        .toString()
}

/** Avancement de la copie des points GPS (la seule étape longue). */
typealias ImportProgress = (copied: Long, total: Long) -> Unit

/** Espace disque disponible — injectable pour que les tests le simulent. */
fun interface FreeSpaceProbe {
    fun availableBytes(dir: File): Long

    companion object {
        /** Mesure réelle via [StatFs]. */
        val STAT_FS = FreeSpaceProbe { dir -> StatFs(dir.path).availableBytes }
    }
}

/** Échec d'import avec une raison lisible et son caractère rejouable. */
class LegacyImportException(message: String, val retriable: Boolean) : RuntimeException(message)

/**
 * Reprise des données de l'app d'origine (`suivi-sport.db`, expo-sqlite) dans
 * la base Room `elan.db`, une seule fois, au premier lancement d'Élan 2.0.
 *
 * Garanties :
 * - idempotent : marqueur [Keys.LEGACY_IMPORT_DONE] ; sans base héritée on
 *   marque et on s'arrête ;
 * - atomique : toute la copie tient dans UNE transaction Room, vérifiée par
 *   comptage avant validation — un échec laisse la base héritée intacte ;
 * - les ids d'origine sont conservés (les sauvegardes S3 et exports coach
 *   produits par l'ancienne app restent cohérents) ;
 * - la base héritée n'est jamais modifiée hors des `ADD COLUMN` idempotents
 *   d'une base restée en version < 7 ; après succès son dossier est renommé
 *   `SQLite.migrated` et purgé un mois plus tard ([cleanupIfDue]).
 *
 * En cas d'échec la transaction est annulée (la base Room revient à son état
 * d'avant) ; le nombre de tentatives et l'erreur sont notés. La politique de
 * nouvelle tentative appartient à l'appelant ([MigrationGate]).
 */
@Singleton
class LegacyDatabaseImporter @Inject constructor(
    @ApplicationContext context: Context,
    private val db: ElanDatabase,
    private val settings: SettingsRepository,
    private val secretStore: SecretStore,
    private val secretsReader: LegacySecretsReader,
    private val freeSpace: FreeSpaceProbe,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    private val paths = LegacyPaths(context)

    /** Lance l'import s'il n'a pas encore eu lieu. Ne lève jamais (hors annulation). */
    suspend fun runIfNeeded(onProgress: ImportProgress = { _, _ -> }): ImportOutcome = withContext(io) {
        if (settings.getSetting(Keys.LEGACY_IMPORT_DONE) == "1") return@withContext ImportOutcome.NothingToDo
        if (!paths.hasLegacyDb) {
            markDone(counts = null)
            return@withContext ImportOutcome.NothingToDo
        }
        val attempt = (settings.getSetting(Keys.LEGACY_IMPORT_ATTEMPTS)?.toIntOrNull() ?: 0) + 1
        try {
            val counts = importOnce(onProgress)
            retireLegacyDir(journalIsEmpty = true)
            markDone(counts)
            if (counts == null) ImportOutcome.NothingToDo else ImportOutcome.Imported(counts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(e, attempt)
        }
    }

    /**
     * L'utilisateur renonce à ses anciennes données : marqué comme fait, la
     * base héritée est mise de côté (`SQLite.migrated`) sans être lue.
     */
    suspend fun markSkipped() = withContext(io) {
        retireLegacyDir(journalIsEmpty = false)
        settings.setSetting(Keys.LEGACY_IMPORT_SKIPPED, "1")
        markDone(counts = null)
    }

    /**
     * Un mois après l'import, supprime le dossier `SQLite.migrated` et les
     * préférences Expo (`SecureStore.xml`, gestionnaire de tâches,
     * notifications). Sans effet avant l'échéance ou une fois fait.
     */
    suspend fun cleanupIfDue(now: Long) = withContext(io) {
        if (settings.getSetting(Keys.LEGACY_CLEANUP_DONE) == "1") return@withContext
        val at = settings.getSetting(Keys.LEGACY_IMPORT_AT)?.toLongOrNull() ?: return@withContext
        if (now - at <= CLEANUP_DELAY.toMillis()) return@withContext
        paths.migratedDir.deleteRecursively()
        paths.legacyPrefFiles().forEach { it.delete() }
        settings.setSetting(Keys.LEGACY_CLEANUP_DONE, "1")
    }

    // ---- une tentative -----------------------------------------------------

    /** `null` : base héritée vide de tout schéma (rien à copier). */
    private suspend fun importOnce(onProgress: ImportProgress): ImportCounts? {
        val needed = 2 * paths.legacyBytes
        val available = freeSpace.availableBytes(paths.filesDir)
        if (available <= needed) {
            throw LegacyImportException(
                "espace disque insuffisant (${available / 1024} Ko libres, ${needed / 1024} Ko requis)",
                retriable = true,
            )
        }

        LegacyDbReader.open(paths.dbFile).use { legacy ->
            val version = legacy.userVersion
            if (version > LEGACY_SCHEMA_VERSION) {
                throw LegacyImportException(
                    "base d'origine en version $version, plus récente que la version $LEGACY_SCHEMA_VERSION connue",
                    retriable = false,
                )
            }
            if (version < 1 || !legacy.hasTable("sessions")) return null
            if (version < LEGACY_SCHEMA_VERSION) legacy.ensureV7Columns()
            legacy.freeze()
            if (!legacy.integrityOk()) {
                throw LegacyImportException("base d'origine corrompue (integrity_check)", retriable = false)
            }

            val expected = ImportCounts(
                sessions = legacy.count("sessions"),
                trackPoints = legacy.count("track_points"),
                muscuSets = legacy.count("muscu_sets"),
                bodyMeasurements = if (legacy.hasTable("body_measurements")) legacy.count("body_measurements") else 0,
                settings = legacy.count("settings"),
            )
            val sequences = TABLES.associateWith { legacy.sequence(it) }

            db.withTransaction {
                copyInto(legacy, expected, sequences, onProgress)
            }
            migrateSecrets()
            return expected
        }
    }

    /** Corps de la transaction : vidage, copie, vérification, séquences, réglages. */
    private suspend fun copyInto(
        legacy: LegacyDbReader,
        expected: ImportCounts,
        sequences: Map<String, Long>,
        onProgress: ImportProgress,
    ) {
        // Reprise après un plantage entre validation et marquage : on repart
        // des quatre tables de données vides (les réglages sont réécrits par
        // upsert juste après, et les marqueurs d'import doivent survivre).
        db.trackPointDao().deleteAll()
        db.muscuSetDao().deleteAll()
        db.sessionDao().deleteAll()
        db.bodyMeasurementDao().deleteAll()

        db.sessionDao().insertAll(legacy.readSessions().map { it.toEntity() })
        db.bodyMeasurementDao().insertAll(legacy.readBodyMeasurements().map { it.toEntity() })
        db.muscuSetDao().insertAll(legacy.readMuscuSets().map { it.toEntity() })

        val raw = db.openHelper.writableDatabase
        copyTrackPoints(raw, legacy, expected.trackPoints, onProgress)

        val actual = ImportCounts(
            sessions = raw.count("sessions"),
            trackPoints = raw.count("track_points"),
            muscuSets = raw.count("muscu_sets"),
            bodyMeasurements = raw.count("body_measurements"),
            settings = expected.settings,
        )
        if (actual != expected) {
            throw LegacyImportException("comptage après copie différent (attendu $expected, obtenu $actual)", retriable = true)
        }

        for (table in TABLES) realignSequence(raw, table, sequences.getValue(table))

        for ((key, value) in legacy.readSettings()) db.settingsDao().upsert(SettingEntity(key, value))
        // Une base héritée = un utilisateur qui a déjà fait ses premiers pas.
        db.settingsDao().upsert(SettingEntity(Keys.ONBOARDING_DONE, "1"))
    }

    /** Copie des points par lots, via une requête préparée (un `INSERT` par point). */
    private fun copyTrackPoints(
        raw: SupportSQLiteDatabase,
        legacy: LegacyDbReader,
        total: Long,
        onProgress: ImportProgress,
    ) {
        onProgress(0, total)
        if (total == 0L) return
        val stmt = raw.compileStatement(
            "INSERT INTO track_points (id, sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        stmt.use {
            var copied = 0L
            legacy.forEachTrackPoint { p ->
                it.clearBindings()
                it.bindLong(1, p.id)
                it.bindLong(2, p.sessionId)
                it.bindLong(3, p.ts)
                it.bindDouble(4, p.lat)
                it.bindDouble(5, p.lon)
                it.bindNullableDouble(6, p.altitude)
                it.bindNullableDouble(7, p.speedKmh)
                it.bindNullableDouble(8, p.hr)
                it.bindNullableDouble(9, p.cadence)
                it.executeInsert()
                copied++
                if (copied % TRACK_POINT_BATCH == 0L) onProgress(copied, total)
            }
            onProgress(copied, total)
        }
    }

    /**
     * `sqlite_sequence` = max(séquence d'origine, plus grand id copié) : un id
     * libéré par une suppression dans l'ancienne app ne sera pas réattribué.
     */
    private fun realignSequence(raw: SupportSQLiteDatabase, table: String, legacySeq: Long) {
        val maxId = raw.query("SELECT COALESCE(MAX(id), 0) FROM $table").use { c -> c.moveToFirst(); c.getLong(0) }
        val seq = maxOf(legacySeq, maxId)
        val updated = raw.compileStatement("UPDATE sqlite_sequence SET seq = ? WHERE name = ?").use {
            it.bindLong(1, seq)
            it.bindString(2, table)
            it.executeUpdateDelete()
        }
        if (updated == 0) {
            raw.compileStatement("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)").use {
                it.bindString(1, table)
                it.bindLong(2, seq)
                it.executeInsert()
            }
        }
    }

    // ---- secrets S3 --------------------------------------------------------

    /**
     * Rapatrie les identifiants S3 : d'abord ceux qu'une très ancienne config
     * aurait laissés dans le JSON `backup_s3` (réécrit sans eux), sinon ceux
     * d'expo-secure-store. N'échoue jamais : au pire on note qu'ils manquent
     * ([Keys.BACKUP_SECRETS_MISSING]) quand une config S3 existait.
     */
    private suspend fun migrateSecrets() {
        try {
            val rawConfig = settings.getSetting(Keys.BACKUP_S3)
            val embedded = SettingsJson.legacyBackupSecrets(rawConfig)
            if (embedded != null) {
                settings.setBackupConfig(SettingsJson.parseBackupConfig(rawConfig))
            }
            val accessKeyId = embedded?.first?.takeIf { it.isNotEmpty() }
                ?: runCatching { secretsReader.read(SecretStore.BACKUP_S3_ACCESS_KEY_ID) }.getOrNull()
            val secretAccessKey = embedded?.second?.takeIf { it.isNotEmpty() }
                ?: runCatching { secretsReader.read(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY) }.getOrNull()

            if (!accessKeyId.isNullOrEmpty()) secretStore.put(SecretStore.BACKUP_S3_ACCESS_KEY_ID, accessKeyId)
            if (!secretAccessKey.isNullOrEmpty()) secretStore.put(SecretStore.BACKUP_S3_SECRET_ACCESS_KEY, secretAccessKey)

            val config = SettingsJson.parseBackupConfig(rawConfig)
            val configured = config.endpoint.isNotBlank() || config.bucket.isNotBlank()
            if (configured && (accessKeyId.isNullOrEmpty() || secretAccessKey.isNullOrEmpty())) {
                settings.setSetting(Keys.BACKUP_SECRETS_MISSING, "1")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { settings.setSetting(Keys.BACKUP_SECRETS_MISSING, "1") }
        }
    }

    // ---- fin de parcours ---------------------------------------------------

    /**
     * `SQLite/` → `SQLite.migrated/`. Après un import, journal et index WAL
     * sont vides (checkpoint TRUNCATE puis fermeture) et supprimés avant ;
     * après un abandon sans lecture, ils partent avec le dossier — le filet
     * de sécurité doit garder les dernières écritures non fusionnées.
     */
    private fun retireLegacyDir(journalIsEmpty: Boolean) {
        if (!paths.sqliteDir.isDirectory) return
        if (journalIsEmpty) {
            paths.walFile.delete()
            paths.shmFile.delete()
        }
        if (paths.migratedDir.exists()) paths.migratedDir.deleteRecursively()
        if (!paths.sqliteDir.renameTo(paths.migratedDir)) {
            throw LegacyImportException("impossible de mettre de côté le dossier SQLite", retriable = true)
        }
    }

    private suspend fun markDone(counts: ImportCounts?) {
        settings.setSetting(Keys.LEGACY_IMPORT_DONE, "1")
        settings.setSetting(Keys.LEGACY_IMPORT_AT, clock.millis().toString())
        if (counts != null) settings.setSetting(Keys.LEGACY_IMPORT_COUNTS, counts.toJson())
        settings.deleteSetting(Keys.LEGACY_IMPORT_ERROR)
    }

    /**
     * La transaction a été annulée par Room : les quatre tables de données
     * sont revenues à leur état d'avant (vides au premier lancement), et la
     * tentative suivante les vide de toute façon avant de copier. On ne ferme
     * ni ne supprime le fichier `elan.db` : le singleton Room ne survit pas à
     * une fermeture (son suivi d'invalidation, initialisé une seule fois,
     * cesse d'alimenter les `Flow` après réouverture), et le journal SQLite
     * garantit déjà une base saine. Reste à noter la tentative et l'erreur.
     */
    private suspend fun fail(e: Throwable, attempt: Int): ImportOutcome.Failed {
        val reason = (e as? LegacyImportException)?.message
            ?: e.message?.takeIf { it.isNotBlank() }
            ?: e.javaClass.simpleName
        val retriable = (e as? LegacyImportException)?.retriable ?: true
        runCatching {
            settings.setSetting(Keys.LEGACY_IMPORT_ATTEMPTS, attempt.toString())
            settings.setSetting(Keys.LEGACY_IMPORT_ERROR, reason)
        }
        return ImportOutcome.Failed(reason, attempt, retriable)
    }

    private fun SupportSQLiteDatabase.count(table: String): Long =
        query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getLong(0) }

    private fun SupportSQLiteStatement.bindNullableDouble(index: Int, value: Double?) =
        if (value == null) bindNull(index) else bindDouble(index, value)

    private fun LegacySession.toEntity() = SessionEntity(
        id = id,
        type = type,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSec = durationSec,
        notes = notes,
        avgHr = avgHr,
        maxHr = maxHr,
        distanceM = distanceM,
        avgSpeedKmh = avgSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        elevationGainM = elevationGainM,
        calories = calories,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        source = source,
        externalId = externalId,
        movingTimeSec = movingTimeSec,
    )

    private fun LegacyMuscuSet.toEntity() =
        MuscuSetEntity(id, sessionId, exercise, setIndex, reps, weightKg, difficulty)

    private fun LegacyBodyMeasurement.toEntity() = BodyMeasurementEntity(id, measuredAt, weightKg)

    companion object {
        /** Dernière version de schéma de l'app d'origine que cet import sait lire. */
        const val LEGACY_SCHEMA_VERSION = 7

        /** Lots de points GPS entre deux remontées d'avancement. */
        const val TRACK_POINT_BATCH = 5_000L

        /** Délai avant la purge des restes de l'app d'origine. */
        val CLEANUP_DELAY: Duration = Duration.ofDays(30)

        private val TABLES = listOf("sessions", "track_points", "muscu_sets", "body_measurements")
    }
}
