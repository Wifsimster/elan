package ovh.battistella.elan.data.legacy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Fabrique une base `suivi-sport.db` telle que l'app d'origine l'aurait
 * laissée : DDL EXACT de `src/lib/db.ts` pour la version demandée (1, 3, 5
 * ou 7 — les `ALTER` des versions suivantes sont appliqués au-delà de la v1),
 * `user_version` posé, mode WAL, données représentatives, et surtout un
 * journal `-wal` NON fusionné : la base est construite dans un dossier de
 * travail puis ses fichiers `.db` + `-wal` sont copiés vers `files/SQLite/`
 * AVANT la fermeture de la connexion (qui, elle, fusionnerait le journal).
 */
object LegacyFixture {

    /** Ids volontairement non contigus : l'import doit les conserver tels quels. */
    const val VELO_ID = 1L
    const val MUSCU_ID = 2L
    const val STRAVA_ID = 3L
    const val IN_PROGRESS_ID = 7L
    const val ORPHAN_MUSCU_ID = 9L

    /** Séance créée puis supprimée : `sqlite_sequence` reste à 12 (> plus grand id). */
    const val DELETED_ID = 12L

    const val VELO_POINTS = 3_000
    const val STRAVA_POINTS = 10
    const val IN_PROGRESS_POINTS = 5
    const val MUSCU_SETS = 6
    const val BODY_MEASUREMENTS = 3

    const val VELO_STARTED_AT = 1_720_000_000_000L
    const val STRAVA_EXTERNAL_ID = "strava-0123456789abcdef01234567"

    val SETTINGS: Map<String, String> = mapOf(
        "profile" to """{"weightKg":78,"heightCm":180,"maxHr":188,"goal":"force","sex":"homme"}""",
        "hr_device" to """{"id":"AA:BB:CC:DD:EE:FF","name":"Polar H10"}""",
        "csc_devices" to """[{"id":"11:22:33:44:55:66","name":"Wahoo CADENCE"}]""",
        "csc_wheel_mm" to "2096",
        "backup_s3" to """{"enabled":true,"endpoint":"https://s3.example.org","region":"eu-west-1","bucket":"elan","objectKey":"elan-backup.json"}""",
        "backup_last" to """{"at":1720000000000,"ok":true}""",
        "map_style_url" to "https://tiles.openfreemap.org/styles/liberty",
        "health_connect" to "1",
        "notifications" to """{"enabled":true,"hour":18}""",
        "week_plan" to """[{"kind":"repos"},{"kind":"muscu","label":"Haut du corps","templateId":"upper"},{"kind":"velo","label":"Vélo"},{"kind":"repos"},{"kind":"muscu","label":"Bas du corps","templateId":"lower"},{"kind":"course","label":"Course"},{"kind":"repos"}]""",
        "goals" to """[{"id":"g1","activity":"velo","metric":"distance","period":"week","target":100}]""",
        "auto_progression" to """{"enabled":true}""",
        "auto_progression_state" to """{"week":"2026-W37","changes":[],"dismissed":false}""",
        "muscu_draft" to """{"version":1,"startedAt":1720000000000,"elapsedSec":600,"exercises":[],"hrSamples":[]}""",
        "privacy_zone_m" to "250",
        "rest_seconds" to "90",
        "onboarding_done" to "1",
    )

    /** Points GPS attendus au total (les points orphelins éventuels en plus). */
    val TOTAL_POINTS: Long = (VELO_POINTS + STRAVA_POINTS + IN_PROGRESS_POINTS).toLong()

    /**
     * @param version `user_version` final (1, 3, 5 ou 7).
     * @param orphanTrackPoint ajoute un point dont la séance n'existe pas
     *   (l'app d'origine n'activait pas toujours `foreign_keys`) : la
     *   contrainte Room le refuse en pleine copie des points.
     * @param settings réglages à écrire (par défaut [SETTINGS]).
     */
    fun create(
        context: Context,
        version: Int = 7,
        orphanTrackPoint: Boolean = false,
        settings: Map<String, String> = SETTINGS,
    ): LegacyPaths {
        require(version in listOf(1, 3, 5, 7)) { "version non prévue : $version" }
        val paths = LegacyPaths(context)
        val staging = File(context.filesDir, "legacy-staging-${System.nanoTime()}").apply { mkdirs() }
        val stagingDb = File(staging, LegacyPaths.DB_NAME)

        val db = SQLiteDatabase.openOrCreateDatabase(stagingDb, null)
        try {
            pragma(db, "PRAGMA journal_mode = WAL")
            createSchema(db, version)
            pragma(db, "PRAGMA user_version = $version")
            seed(db, version, orphanTrackPoint, settings)

            // Copie « à chaud » : le journal contient encore les dernières écritures.
            paths.sqliteDir.mkdirs()
            stagingDb.copyTo(paths.dbFile, overwrite = true)
            File(staging, "${LegacyPaths.DB_NAME}-wal").copyTo(paths.walFile, overwrite = true)
        } finally {
            db.close()
            staging.deleteRecursively()
        }
        return paths
    }

    /** Ouvre (lecture seule logique) une base héritée pour l'inspecter dans un test. */
    fun open(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)

    private fun pragma(db: SQLiteDatabase, sql: String) {
        db.rawQuery(sql, null).use { it.moveToFirst() }
    }

    // ---- DDL de db.ts, bloc par bloc ---------------------------------------

    private fun createSchema(db: SQLiteDatabase, version: Int) {
        // version < 1
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sessions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              type TEXT NOT NULL,
              startedAt INTEGER NOT NULL,
              endedAt INTEGER,
              durationSec INTEGER NOT NULL DEFAULT 0,
              notes TEXT,
              avgHr REAL,
              maxHr REAL,
              distanceM REAL,
              avgSpeedKmh REAL,
              maxSpeedKmh REAL,
              elevationGainM REAL,
              calories REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_startedAt ON sessions (startedAt DESC)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_points (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
              ts INTEGER NOT NULL,
              lat REAL NOT NULL,
              lon REAL NOT NULL,
              altitude REAL,
              speedKmh REAL,
              hr REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_track_session ON track_points (sessionId, ts)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS muscu_sets (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              sessionId INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
              exercise TEXT NOT NULL,
              setIndex INTEGER NOT NULL,
              reps INTEGER NOT NULL,
              weightKg REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sets_session ON muscu_sets (sessionId, exercise, setIndex)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS settings (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
            """.trimIndent()
        )
        if (version < 2) return
        db.execSQL("ALTER TABLE sessions ADD COLUMN avgCadence REAL")
        db.execSQL("ALTER TABLE sessions ADD COLUMN maxCadence REAL")
        db.execSQL("ALTER TABLE track_points ADD COLUMN cadence REAL")
        if (version < 3) return
        db.execSQL("ALTER TABLE sessions ADD COLUMN source TEXT")
        db.execSQL("ALTER TABLE sessions ADD COLUMN externalId TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_external ON sessions (externalId) WHERE externalId IS NOT NULL"
        )
        if (version < 4) return
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS body_measurements (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              measuredAt INTEGER NOT NULL,
              weightKg REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_body_measuredAt ON body_measurements (measuredAt DESC)")
        if (version < 5) return
        db.execSQL("ALTER TABLE sessions ADD COLUMN movingTimeSec INTEGER")
        if (version < 6) return
        db.execSQL("ALTER TABLE muscu_sets ADD COLUMN difficulty TEXT")
        if (version < 7) return
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sets_exercise ON muscu_sets (exercise)")
    }

    // ---- données ------------------------------------------------------------

    private fun seed(db: SQLiteDatabase, version: Int, orphanTrackPoint: Boolean, settings: Map<String, String>) {
        val columns = listOf("sessions", "track_points", "muscu_sets").associateWith { columnsOf(db, it) }

        db.beginTransaction()
        try {
            insert(
                db, "sessions", columns,
                "id" to VELO_ID, "type" to "velo", "startedAt" to VELO_STARTED_AT,
                "endedAt" to VELO_STARTED_AT + 3_600_000L, "durationSec" to 3600, "notes" to "Sortie du dimanche",
                "avgHr" to 142.5, "maxHr" to 171.0, "distanceM" to 28_450.0, "avgSpeedKmh" to 30.1,
                "maxSpeedKmh" to 52.3, "elevationGainM" to 312.0, "calories" to 812.0,
                "avgCadence" to 84.0, "maxCadence" to 112.0, "movingTimeSec" to 3_402,
            )
            insert(
                db, "sessions", columns,
                "id" to MUSCU_ID, "type" to "muscu", "startedAt" to VELO_STARTED_AT + 86_400_000L,
                "endedAt" to VELO_STARTED_AT + 86_400_000L + 2_700_000L, "durationSec" to 2700,
                "notes" to "Haut du corps", "avgHr" to 118.0, "maxHr" to 150.0, "calories" to 310.0,
            )
            insert(
                db, "sessions", columns,
                "id" to STRAVA_ID, "type" to "velo", "startedAt" to VELO_STARTED_AT - 7 * 86_400_000L,
                "endedAt" to VELO_STARTED_AT - 7 * 86_400_000L + 5_400_000L, "durationSec" to 5400,
                "notes" to "Importé depuis Strava", "distanceM" to 41_200.0, "avgSpeedKmh" to 27.5,
                "maxSpeedKmh" to 48.0, "elevationGainM" to 540.0, "calories" to 1_120.0,
                "source" to "strava", "externalId" to STRAVA_EXTERNAL_ID, "movingTimeSec" to 5_100,
            )
            insert(
                db, "sessions", columns,
                "id" to IN_PROGRESS_ID, "type" to "velo", "startedAt" to VELO_STARTED_AT + 2 * 86_400_000L,
                "durationSec" to 0,
            )
            insert(
                db, "sessions", columns,
                "id" to ORPHAN_MUSCU_ID, "type" to "muscu", "startedAt" to VELO_STARTED_AT + 3 * 86_400_000L,
                "durationSec" to 0,
            )
            insert(db, "sessions", columns, "id" to DELETED_ID, "type" to "marche", "startedAt" to 1L)
            db.delete("sessions", "id = ?", arrayOf(DELETED_ID.toString()))

            insertPoints(db, columns.getValue("track_points"), VELO_ID, VELO_STARTED_AT, VELO_POINTS, withSensors = true)
            insertPoints(db, columns.getValue("track_points"), STRAVA_ID, VELO_STARTED_AT - 7 * 86_400_000L, STRAVA_POINTS, withSensors = false)
            insertPoints(db, columns.getValue("track_points"), IN_PROGRESS_ID, VELO_STARTED_AT + 2 * 86_400_000L, IN_PROGRESS_POINTS, withSensors = true)
            if (orphanTrackPoint) {
                insert(
                    db, "track_points", columns,
                    "sessionId" to 4_242L, "ts" to 1L, "lat" to 48.0, "lon" to 2.0,
                )
            }

            val exercises = listOf("Développé couché", "Rowing haltère", "Élévations latérales")
            var setNo = 0
            for ((i, exercise) in exercises.withIndex()) {
                for (setIndex in 1..2) {
                    setNo++
                    insert(
                        db, "muscu_sets", columns,
                        "sessionId" to MUSCU_ID, "exercise" to exercise, "setIndex" to setIndex,
                        "reps" to 8 + setIndex, "weightKg" to 20.0 + 10 * i,
                        "difficulty" to if (i == 2) null else "moyen",
                    )
                }
            }
            check(setNo == MUSCU_SETS)

            for ((key, value) in settings) {
                db.execSQL("INSERT INTO settings (key, value) VALUES (?, ?)", arrayOf(key, value))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Dernière transaction à part : au moins celle-ci n'est que dans le journal.
        if (version >= 4) {
            db.beginTransaction()
            try {
                for (i in 1..BODY_MEASUREMENTS) {
                    db.execSQL(
                        "INSERT INTO body_measurements (id, measuredAt, weightKg) VALUES (?, ?, ?)",
                        arrayOf<Any>(i, VELO_STARTED_AT + i * 86_400_000L, 78.0 - 0.2 * i),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun insertPoints(
        db: SQLiteDatabase,
        columns: Set<String>,
        sessionId: Long,
        startedAt: Long,
        count: Int,
        withSensors: Boolean,
    ) {
        val hasCadence = "cadence" in columns
        val sql = if (hasCadence) {
            "INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        } else {
            "INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr) VALUES (?, ?, ?, ?, ?, ?, ?)"
        }
        val stmt = db.compileStatement(sql)
        for (i in 0 until count) {
            stmt.clearBindings()
            stmt.bindLong(1, sessionId)
            stmt.bindLong(2, startedAt + i * 1_000L)
            stmt.bindDouble(3, 48.8566 + i * 1e-5)
            stmt.bindDouble(4, 2.3522 + i * 1e-5)
            // Un point sur dix sans altitude ni vitesse : les NULL doivent survivre.
            if (i % 10 == 9) stmt.bindNull(5) else stmt.bindDouble(5, 35.0 + i % 50)
            if (i % 10 == 9) stmt.bindNull(6) else stmt.bindDouble(6, 25.0 + (i % 20) * 0.5)
            if (withSensors) stmt.bindDouble(7, 120.0 + i % 40) else stmt.bindNull(7)
            if (hasCadence) {
                if (withSensors) stmt.bindDouble(8, 80.0 + i % 15) else stmt.bindNull(8)
            }
            stmt.executeInsert()
        }
        stmt.close()
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val idx = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(idx)) }
        }

    /** Insertion tolérante : les colonnes absentes de cette version du schéma sont ignorées. */
    private fun insert(
        db: SQLiteDatabase,
        table: String,
        columns: Map<String, Set<String>>,
        vararg values: Pair<String, Any?>,
    ) {
        val known = columns.getValue(table)
        val cv = ContentValues()
        for ((k, v) in values) {
            if (k !in known) continue
            when (v) {
                null -> cv.putNull(k)
                is Long -> cv.put(k, v)
                is Int -> cv.put(k, v)
                is Double -> cv.put(k, v)
                is String -> cv.put(k, v)
                else -> error("type non géré : $v")
            }
        }
        check(db.insertOrThrow(table, null, cv) != -1L)
    }
}
