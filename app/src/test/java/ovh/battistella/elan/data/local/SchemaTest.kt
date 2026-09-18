package ovh.battistella.elan.data.local

import android.database.Cursor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.testing.TestSupport

/**
 * Garde le contrat des noms hérités : les tables, colonnes (types, nullabilité,
 * défauts) et index de la base Room sont exactement ceux du schéma v7 de
 * l'app d'origine (`db.ts`). Une sauvegarde ou un export coach produit par
 * l'ancienne app doit rester relisible colonne pour colonne.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaTest {

    private lateinit var db: ElanDatabase

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** (nom, type, notnull, défaut, pk) tel que `PRAGMA table_info` le rapporte. */
    private data class Column(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Boolean)

    private fun query(sql: String): Cursor = db.openHelper.readableDatabase.query(sql)

    private fun columns(table: String): List<Column> = query("PRAGMA table_info(`$table`)").use { c ->
        generateSequence { if (c.moveToNext()) c else null }.map {
            Column(
                name = it.getString(1),
                type = it.getString(2),
                notNull = it.getInt(3) == 1,
                default = it.getString(4),
                pk = it.getInt(5) == 1,
            )
        }.toList()
    }

    /** (nom d'index → unique) des index NOMMÉS de la table (hors auto-index de clé primaire). */
    private fun indexes(table: String): Map<String, Boolean> = query("PRAGMA index_list(`$table`)").use { c ->
        generateSequence { if (c.moveToNext()) c else null }
            .filter { it.getString(3) != "pk" }
            .associate { it.getString(1) to (it.getInt(2) == 1) }
    }

    private fun indexColumns(index: String): List<String> = query("PRAGMA index_info(`$index`)").use { c ->
        generateSequence { if (c.moveToNext()) c else null }.map { it.getString(2) }.toList()
    }

    /** Colonnes de l'index dans l'ordre de définition, avec le sens (`PRAGMA index_xinfo`). */
    private fun indexColumnsWithOrder(index: String): List<Pair<String, String>> =
        query("PRAGMA index_xinfo(`$index`)").use { c ->
            generateSequence { if (c.moveToNext()) c else null }
                .filter { it.getInt(1) >= 0 } // exclut la colonne rowid implicite
                .map { it.getString(2) to (if (it.getInt(3) == 1) "DESC" else "ASC") }
                .toList()
        }

    private fun foreignKeys(table: String): List<Triple<String, String, String>> =
        query("PRAGMA foreign_key_list(`$table`)").use { c ->
            generateSequence { if (c.moveToNext()) c else null }
                .map { Triple(it.getString(2), it.getString(3), it.getString(6)) } // table, from, on_delete
                .toList()
        }

    /** Tables applicatives : hors internes SQLite, Room et `android_metadata` (locale, posée par Android). */
    private fun tables(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name <> 'android_metadata'").use { c ->
            generateSequence { if (c.moveToNext()) c else null }.map { it.getString(0) }.toSet()
        }

    private fun col(name: String, type: String, notNull: Boolean = false, default: String? = null, pk: Boolean = false) =
        Column(name, type, notNull, default, pk)

    @Test
    fun `les cinq tables héritées existent`() {
        assertEquals(setOf("sessions", "track_points", "muscu_sets", "body_measurements", "settings"), tables())
    }

    @Test
    fun `sessions a les colonnes du schéma v7 dans l'ordre`() {
        assertEquals(
            listOf(
                col("id", "INTEGER", notNull = true, pk = true),
                col("type", "TEXT", notNull = true),
                col("startedAt", "INTEGER", notNull = true),
                col("endedAt", "INTEGER"),
                col("durationSec", "INTEGER", notNull = true, default = "0"),
                col("notes", "TEXT"),
                col("avgHr", "REAL"),
                col("maxHr", "REAL"),
                col("distanceM", "REAL"),
                col("avgSpeedKmh", "REAL"),
                col("maxSpeedKmh", "REAL"),
                col("elevationGainM", "REAL"),
                col("calories", "REAL"),
                col("avgCadence", "REAL"),
                col("maxCadence", "REAL"),
                col("source", "TEXT"),
                col("externalId", "TEXT"),
                col("movingTimeSec", "INTEGER"),
            ),
            columns("sessions"),
        )
        assertEquals(mapOf("idx_sessions_startedAt" to false, "idx_sessions_external" to true), indexes("sessions"))
        assertEquals(listOf("startedAt" to "DESC"), indexColumnsWithOrder("idx_sessions_startedAt"))
        assertEquals(listOf("externalId"), indexColumns("idx_sessions_external"))
    }

    @Test
    fun `track_points référence sessions en cascade`() {
        assertEquals(
            listOf(
                col("id", "INTEGER", notNull = true, pk = true),
                col("sessionId", "INTEGER", notNull = true),
                col("ts", "INTEGER", notNull = true),
                col("lat", "REAL", notNull = true),
                col("lon", "REAL", notNull = true),
                col("altitude", "REAL"),
                col("speedKmh", "REAL"),
                col("hr", "REAL"),
                col("cadence", "REAL"),
            ),
            columns("track_points"),
        )
        assertEquals(mapOf("idx_track_session" to false), indexes("track_points"))
        assertEquals(listOf("sessionId", "ts"), indexColumns("idx_track_session"))
        assertEquals(listOf(Triple("sessions", "sessionId", "CASCADE")), foreignKeys("track_points"))
    }

    @Test
    fun `muscu_sets référence sessions en cascade avec ses deux index`() {
        assertEquals(
            listOf(
                col("id", "INTEGER", notNull = true, pk = true),
                col("sessionId", "INTEGER", notNull = true),
                col("exercise", "TEXT", notNull = true),
                col("setIndex", "INTEGER", notNull = true),
                col("reps", "INTEGER", notNull = true),
                col("weightKg", "REAL", notNull = true),
                col("difficulty", "TEXT"),
            ),
            columns("muscu_sets"),
        )
        assertEquals(mapOf("idx_sets_session" to false, "idx_sets_exercise" to false), indexes("muscu_sets"))
        assertEquals(listOf("sessionId", "exercise", "setIndex"), indexColumns("idx_sets_session"))
        assertEquals(listOf("exercise"), indexColumns("idx_sets_exercise"))
        assertEquals(listOf(Triple("sessions", "sessionId", "CASCADE")), foreignKeys("muscu_sets"))
    }

    @Test
    fun `body_measurements et settings`() {
        assertEquals(
            listOf(
                col("id", "INTEGER", notNull = true, pk = true),
                col("measuredAt", "INTEGER", notNull = true),
                col("weightKg", "REAL", notNull = true),
            ),
            columns("body_measurements"),
        )
        assertEquals(mapOf("idx_body_measuredAt" to false), indexes("body_measurements"))
        assertEquals(listOf("measuredAt" to "DESC"), indexColumnsWithOrder("idx_body_measuredAt"))

        assertEquals(
            listOf(
                col("key", "TEXT", notNull = true, pk = true),
                col("value", "TEXT", notNull = true),
            ),
            columns("settings"),
        )
    }

    @Test
    fun `les clés étrangères sont actives et les ids auto-incrémentés`() {
        query("PRAGMA foreign_keys").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        // AUTOINCREMENT crée la table interne sqlite_sequence.
        query("SELECT name FROM sqlite_master WHERE name = 'sqlite_sequence'").use { c ->
            assertTrue(c.moveToFirst())
        }
    }
}
