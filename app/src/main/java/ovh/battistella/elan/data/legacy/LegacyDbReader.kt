package ovh.battistella.elan.data.legacy

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

class LegacyDbReader private constructor(private val db: SQLiteDatabase) : Closeable {

    val userVersion: Int get() = db.version

    fun hasTable(table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.moveToFirst() }

    fun hasColumn(table: String, column: String): Boolean = columns(table).contains(column)

    fun columns(table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(nameIdx)) }
        }

    fun ensureV7Columns() {
        for ((table, column, decl) in V7_COLUMNS) {
            if (!hasColumn(table, column)) db.execSQL("ALTER TABLE $table ADD COLUMN $column $decl")
        }
    }

    fun freeze() {
        db.rawQuery("PRAGMA query_only = 1", null).use { it.moveToFirst() }
    }

    fun integrityOk(): Boolean =
        db.rawQuery("PRAGMA integrity_check", null).use { c ->
            c.moveToFirst() && c.count == 1 && c.getString(0).equals("ok", ignoreCase = true)
        }

    fun count(table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getLong(0) }

    fun sequence(table: String): Long =
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }

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
        val V7_COLUMNS: List<Triple<String, String, String>> = listOf(
            Triple("sessions", "avgCadence", "REAL"),
            Triple("sessions", "maxCadence", "REAL"),
            Triple("track_points", "cadence", "REAL"),
            Triple("sessions", "source", "TEXT"),
            Triple("sessions", "externalId", "TEXT"),
            Triple("sessions", "movingTimeSec", "INTEGER"),
            Triple("muscu_sets", "difficulty", "TEXT"),
        )

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
