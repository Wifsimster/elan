package ovh.battistella.elan.data.legacy

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/**
 * Lecture de `suivi-sport.db`, la base SQLite de l'app d'origine.
 *
 * Le fichier est ouvert en LECTURE-ÉCRITURE : en lecture seule, SQLite refuse
 * d'ouvrir une base en mode WAL dont le `-shm` n'est pas déjà là (3.18 sur
 * Android 8). On en profite pour vider le journal (`wal_checkpoint(TRUNCATE)`)
 * — les lignes écrites juste avant la mise à jour n'ont jamais été fusionnées
 * dans le fichier principal — puis on verrouille la connexion en
 * `query_only` : plus aucune écriture possible, quoi qu'il arrive ensuite.
 *
 * Les lecteurs tolèrent une colonne absente (base en version < 7) : elle vaut
 * `NULL`, l'équivalent de ce que la migration d'origine aurait laissé.
 */
class LegacyDbReader private constructor(private val db: SQLiteDatabase) : Closeable {

    /** `PRAGMA user_version` : version du schéma d'origine (7 pour une base à jour). */
    val userVersion: Int get() = db.version

    fun hasTable(table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.moveToFirst() }

    fun hasColumn(table: String, column: String): Boolean = columns(table).contains(column)

    /** Noms de colonnes d'une table via `PRAGMA table_info`. */
    fun columns(table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }

    /**
     * Ajoute les colonnes que les migrations v2/v3/v5/v6 de l'app d'origine
     * auraient créées, si elles manquent (base restée en version < 7). Aucun
     * rétro-calcul : les valeurs restent `NULL`. À appeler avant [freeze].
     */
    fun ensureV7Columns() {
        for ((table, column, decl) in V7_COLUMNS) {
            if (!hasColumn(table, column)) db.execSQL("ALTER TABLE $table ADD COLUMN $column $decl")
        }
    }

    /** Verrouille la connexion : toute écriture ultérieure échoue. */
    fun freeze() {
        db.rawQuery("PRAGMA query_only = 1", null).use { it.moveToFirst() }
    }

    /** `PRAGMA integrity_check` répond « ok » sur une seule ligne. */
    fun integrityOk(): Boolean =
        db.rawQuery("PRAGMA integrity_check", null).use { c ->
            c.moveToFirst() && c.count == 1 && c.getString(0).equals("ok", ignoreCase = true)
        }

    fun count(table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getLong(0) }

    /** Contenu de `sqlite_sequence` pour la table (0 si absent). */
    fun sequence(table: String): Long =
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }

    // ---- lecteurs par table --------------------------------------------

    fun readSessions(): List<LegacySession> =
        db.rawQuery("SELECT * FROM sessions ORDER BY id", null).use { c ->
            val r = Row(c)
            buildList {
                while (c.moveToNext()) add(
                    LegacySession(
                        id = r.long("id")!!,
                        type = r.string("type")!!,
                        startedAt = r.long("startedAt")!!,
                        endedAt = r.long("endedAt"),
                        durationSec = r.int("durationSec") ?: 0,
                        notes = r.string("notes"),
                        avgHr = r.double("avgHr"),
                        maxHr = r.double("maxHr"),
                        distanceM = r.double("distanceM"),
                        avgSpeedKmh = r.double("avgSpeedKmh"),
                        maxSpeedKmh = r.double("maxSpeedKmh"),
                        elevationGainM = r.double("elevationGainM"),
                        calories = r.double("calories"),
                        avgCadence = r.double("avgCadence"),
                        maxCadence = r.double("maxCadence"),
                        source = r.string("source"),
                        externalId = r.string("externalId"),
                        movingTimeSec = r.int("movingTimeSec"),
                    )
                )
            }
        }

    /**
     * Parcours des points GPS par curseur (sans les charger en mémoire : une
     * base réelle en compte des centaines de milliers). [block] est appelé
     * pour chaque point, dans l'ordre des ids.
     */
    fun forEachTrackPoint(block: (LegacyTrackPoint) -> Unit) {
        db.rawQuery("SELECT * FROM track_points ORDER BY id", null).use { c ->
            val r = Row(c)
            while (c.moveToNext()) {
                block(
                    LegacyTrackPoint(
                        id = r.long("id")!!,
                        sessionId = r.long("sessionId")!!,
                        ts = r.long("ts")!!,
                        lat = r.double("lat")!!,
                        lon = r.double("lon")!!,
                        altitude = r.double("altitude"),
                        speedKmh = r.double("speedKmh"),
                        hr = r.double("hr"),
                        cadence = r.double("cadence"),
                    )
                )
            }
        }
    }

    fun readMuscuSets(): List<LegacyMuscuSet> =
        db.rawQuery("SELECT * FROM muscu_sets ORDER BY id", null).use { c ->
            val r = Row(c)
            buildList {
                while (c.moveToNext()) add(
                    LegacyMuscuSet(
                        id = r.long("id")!!,
                        sessionId = r.long("sessionId")!!,
                        exercise = r.string("exercise")!!,
                        setIndex = r.int("setIndex")!!,
                        reps = r.int("reps")!!,
                        weightKg = r.double("weightKg")!!,
                        difficulty = r.string("difficulty"),
                    )
                )
            }
        }

    /** Vide si la table n'existe pas (base en version < 4). */
    fun readBodyMeasurements(): List<LegacyBodyMeasurement> {
        if (!hasTable("body_measurements")) return emptyList()
        return db.rawQuery("SELECT * FROM body_measurements ORDER BY id", null).use { c ->
            val r = Row(c)
            buildList {
                while (c.moveToNext()) add(
                    LegacyBodyMeasurement(
                        id = r.long("id")!!,
                        measuredAt = r.long("measuredAt")!!,
                        weightKg = r.double("weightKg")!!,
                    )
                )
            }
        }
    }

    fun readSettings(): List<Pair<String, String>> =
        db.rawQuery("SELECT key, value FROM settings ORDER BY key", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }

    override fun close() = db.close()

    /** Accès aux colonnes par nom, `null` si la colonne manque ou vaut NULL. */
    private class Row(private val c: Cursor) {
        private val index = HashMap<String, Int>()
        private fun idx(name: String) = index.getOrPut(name) { c.getColumnIndex(name) }
        private fun present(name: String): Boolean {
            val i = idx(name)
            return i >= 0 && !c.isNull(i)
        }

        fun long(name: String): Long? = if (present(name)) c.getLong(idx(name)) else null
        fun int(name: String): Int? = if (present(name)) c.getInt(idx(name)) else null
        fun double(name: String): Double? = if (present(name)) c.getDouble(idx(name)) else null
        fun string(name: String): String? = if (present(name)) c.getString(idx(name)) else null
    }

    companion object {
        /** Colonnes ajoutées par les migrations v2, v3, v5 et v6 de `db.ts`. */
        val V7_COLUMNS: List<Triple<String, String, String>> = listOf(
            Triple("sessions", "avgCadence", "REAL"),
            Triple("sessions", "maxCadence", "REAL"),
            Triple("track_points", "cadence", "REAL"),
            Triple("sessions", "source", "TEXT"),
            Triple("sessions", "externalId", "TEXT"),
            Triple("sessions", "movingTimeSec", "INTEGER"),
            Triple("muscu_sets", "difficulty", "TEXT"),
        )

        /**
         * Ouvre la base, fusionne le journal WAL dans le fichier principal et
         * le tronque. La connexion reste modifiable jusqu'à [freeze] pour
         * permettre [ensureV7Columns].
         */
        fun open(file: File): LegacyDbReader {
            val db = SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            try {
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            } catch (e: Exception) {
                db.close()
                throw e
            }
            return LegacyDbReader(db)
        }
    }
}

// ---- lignes héritées, brutes (aucune conversion vers le domaine) ---------

data class LegacySession(
    val id: Long,
    val type: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationSec: Int,
    val notes: String?,
    val avgHr: Double?,
    val maxHr: Double?,
    val distanceM: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val elevationGainM: Double?,
    val calories: Double?,
    val avgCadence: Double?,
    val maxCadence: Double?,
    val source: String?,
    val externalId: String?,
    val movingTimeSec: Int?,
)

data class LegacyTrackPoint(
    val id: Long,
    val sessionId: Long,
    val ts: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val speedKmh: Double?,
    val hr: Double?,
    val cadence: Double?,
)

data class LegacyMuscuSet(
    val id: Long,
    val sessionId: Long,
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val difficulty: String?,
)

data class LegacyBodyMeasurement(val id: Long, val measuredAt: Long, val weightKg: Double)
