package ovh.battistella.elan.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: ElanDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun `une base v8 créée depuis le schéma exporté est acceptée par Room et relue`() = runBlocking {
        createDatabaseAt(version = 8) {
            execSQL(
                """
                INSERT INTO sessions (type, startedAt, endedAt, durationSec, notes, distanceM, source, externalId, movingTimeSec)
                VALUES ('velo', 1700000000000, 1700003600000, 3600, 'Sortie', 30000.0, 'strava', 'strava-abc', 3400)
                """.trimIndent()
            )
            execSQL("INSERT INTO track_points (sessionId, ts, lat, lon, altitude, speedKmh, hr, cadence) VALUES (1, 1700000000000, 48.85, 2.35, 35.0, 18.0, 120.0, NULL)")
            execSQL("INSERT INTO muscu_sets (sessionId, exercise, setIndex, reps, weightKg, difficulty) VALUES (1, 'Squat', 1, 10, 20.0, 'facile')")
            execSQL("INSERT INTO body_measurements (measuredAt, weightKg) VALUES (1700000000000, 72.5)")
            execSQL("INSERT INTO settings (key, value) VALUES ('profile', '{\"weightKg\":72.5}')")
        }

        val db = openMigratedDatabase()

        val session = db.sessionDao().getById(1)!!
        assertEquals("velo", session.type)
        assertEquals(3400, session.movingTimeSec)
        assertEquals("strava-abc", session.externalId)
        assertEquals(1, db.trackPointDao().getForSession(1).size)
        assertEquals("facile", db.muscuSetDao().getForSession(1).single().difficulty)
        assertEquals(72.5, db.bodyMeasurementDao().latest()!!.weightKg, 0.0)
        assertEquals("{\"weightKg\":72.5}", db.settingsDao().get("profile"))
    }

    private fun createDatabaseAt(version: Int, populate: SQLiteDatabase.() -> Unit) {
        val file: File = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        file.delete()
        val schema = JSONObject(readSchema(version)).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").withTable(table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").withTable(table))
                }
            }
            db.populate()
            db.version = schema.getInt("version")
        }
    }

    private fun openMigratedDatabase(vararg migrations: Migration): ElanDatabase {
        val directExecutor = Executor { it.run() }
        return Room.databaseBuilder(context, ElanDatabase::class.java, DB_NAME)
            .addMigrations(*migrations)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
            .also { database = it }
    }

    private fun readSchema(version: Int): String {
        val path = "${ElanDatabase::class.java.canonicalName}/$version.json"
        return checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Schéma exporté $path introuvable — app/schemas est-il sur le classpath de test ?"
        }.bufferedReader().use { it.readText() }
    }

    private fun String.withTable(table: String) = replace("\${TABLE_NAME}", table)

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
