package ovh.battistella.elan.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.repository.DbSnapshot
import ovh.battistella.elan.data.repository.SettingEntry
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import java.io.ByteArrayOutputStream
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
class BackupSnapshotCodecTest {

    private val snapshot = DbSnapshot(
        sessions = listOf(
            Session(
                id = 3, type = ActivityType.VELO, startedAt = 1_700_000_000_000L, endedAt = 1_700_003_600_000L,
                durationSec = 3600, movingTimeSec = 3400, notes = "Boucle « matin »", avgHr = 132.5, maxHr = 171.0,
                distanceM = 25_431.7, avgSpeedKmh = 26.9, maxSpeedKmh = 48.25, elevationGainM = 210.0, avgCadence = null,
                maxCadence = null, calories = 612.0, source = null, externalId = null,
            ),
            Session(
                id = 4, type = ActivityType.MUSCU, startedAt = 1_700_100_000_000L, endedAt = null, durationSec = 0,
                movingTimeSec = null, notes = null, avgHr = null, maxHr = null, distanceM = null, avgSpeedKmh = null,
                maxSpeedKmh = null, elevationGainM = null, avgCadence = null, maxCadence = null, calories = null,
                source = "strava", externalId = "strava-abc",
            ),
        ),
        trackPoints = listOf(
            TrackPoint(1, 3, 1_700_000_000_000L, 48.8566, 2.3522, 35.0, 18.0, 120.0, null),
            TrackPoint(2, 3, 1_700_000_001_000L, 48.857, 2.35225, null, null, null, 84.0),
        ),
        muscuSets = listOf(
            MuscuSet(7, 4, "Goblet squat", 1, 10, 20.0, Difficulty.MOYEN),
            MuscuSet(8, 4, "Goblet squat", 2, 8, 22.5, null),
        ),
        bodyMeasurements = listOf(BodyMeasurement(1, 1_699_000_000_000L, 72.4)),
        settings = listOf(SettingEntry("profile", "{\"weightKg\":72.4}"), SettingEntry("privacy_zone_m", "200")),
    )

    /** JSON de référence : exactement ce que produisait `JSON.stringify` de l'app d'origine. */
    private val golden = """{"format":1,"app":"suivi-sport","exportedAt":1700200000000,"schema":7,"data":{""" +
        """"sessions":[""" +
        """{"id":3,"type":"velo","startedAt":1700000000000,"endedAt":1700003600000,"durationSec":3600,"notes":"Boucle « matin »","avgHr":132.5,"maxHr":171,"distanceM":25431.7,"avgSpeedKmh":26.9,"maxSpeedKmh":48.25,"elevationGainM":210,"calories":612,"avgCadence":null,"maxCadence":null,"source":null,"externalId":null,"movingTimeSec":3400},""" +
        """{"id":4,"type":"muscu","startedAt":1700100000000,"endedAt":null,"durationSec":0,"notes":null,"avgHr":null,"maxHr":null,"distanceM":null,"avgSpeedKmh":null,"maxSpeedKmh":null,"elevationGainM":null,"calories":null,"avgCadence":null,"maxCadence":null,"source":"strava","externalId":"strava-abc","movingTimeSec":null}],""" +
        """"trackPoints":[""" +
        """{"id":1,"sessionId":3,"ts":1700000000000,"lat":48.8566,"lon":2.3522,"altitude":35,"speedKmh":18,"hr":120,"cadence":null},""" +
        """{"id":2,"sessionId":3,"ts":1700000001000,"lat":48.857,"lon":2.35225,"altitude":null,"speedKmh":null,"hr":null,"cadence":84}],""" +
        """"muscuSets":[""" +
        """{"id":7,"sessionId":4,"exercise":"Goblet squat","setIndex":1,"reps":10,"weightKg":20,"difficulty":"moyen"},""" +
        """{"id":8,"sessionId":4,"exercise":"Goblet squat","setIndex":2,"reps":8,"weightKg":22.5,"difficulty":null}],""" +
        """"bodyMeasurements":[{"id":1,"measuredAt":1699000000000,"weightKg":72.4}],""" +
        """"settings":[{"key":"profile","value":"{\"weightKg\":72.4}"},{"key":"privacy_zone_m","value":"200"}]}}"""

    private fun encode(s: DbSnapshot, at: Long = 1_700_200_000_000L): String {
        val out = ByteArrayOutputStream()
        BackupSnapshotCodec.write(s, at, out)
        return out.toString("UTF-8")
    }

    @Test
    fun `écrit exactement le JSON de l'app d'origine (entiers sans point zéro)`() {
        assertEquals(golden, encode(snapshot))
    }

    @Test
    fun `relit le JSON de référence en un instantané identique`() {
        val env = BackupSnapshotCodec.read(StringReader(golden))
        assertEquals(1, env.format)
        assertEquals("suivi-sport", env.app)
        assertEquals(1_700_200_000_000L, env.exportedAt)
        assertEquals(7, env.schema)
        assertEquals(snapshot, env.data)
    }

    @Test
    fun `aller-retour export → JSON → import`() {
        val json = encode(snapshot)
        assertEquals(snapshot, BackupSnapshotCodec.read(StringReader(json)).data)
    }

    @Test
    fun `tolère une sauvegarde héritée sans bodyMeasurements ni hr, cadence, difficulty, schema`() {
        val legacy = """{"format":1,"app":"suivi-sport","exportedAt":1,"data":{""" +
            """"sessions":[{"id":1,"type":"velo","startedAt":1000,"endedAt":2000,"durationSec":1}],""" +
            """"trackPoints":[{"id":1,"sessionId":1,"ts":1000,"lat":1.5,"lon":2.5,"altitude":3.5,"speedKmh":4.5}],""" +
            """"muscuSets":[{"id":1,"sessionId":1,"exercise":"X","setIndex":1,"reps":5,"weightKg":10}],""" +
            """"settings":[{"key":"a","value":"b"}],"inconnu":{"x":[1,2,{"y":null}]}}}"""
        val env = BackupSnapshotCodec.read(StringReader(legacy))
        assertNull(env.schema)
        val data = env.data!!
        assertNull(data.bodyMeasurements)
        assertEquals(1, data.sessions.size)
        assertNull(data.sessions[0].movingTimeSec)
        assertNull(data.sessions[0].notes)
        assertNull(data.trackPoints[0].hr)
        assertNull(data.trackPoints[0].cadence)
        assertNull(data.muscuSets[0].difficulty)
        assertEquals(listOf(SettingEntry("a", "b")), data.settings)
    }

    @Test
    fun `ignore les champs inconnus et les nombres en notation décimale`() {
        val json = """{"format":1.0,"app":"suivi-sport","exportedAt":1.7e12,"schema":7,"data":{"sessions":[""" +
            """{"id":2.0,"type":"course","startedAt":1000.0,"endedAt":null,"durationSec":60.0,"extra":"x","setCount":3}],""" +
            """"trackPoints":[],"muscuSets":[],"bodyMeasurements":[],"settings":[]}}"""
        val env = BackupSnapshotCodec.read(StringReader(json))
        assertEquals(1, env.format)
        assertEquals(1_700_000_000_000L, env.exportedAt)
        val s = env.data!!.sessions.single()
        assertEquals(2L, s.id)
        assertEquals(ActivityType.COURSE, s.type)
        assertEquals(60, s.durationSec)
        assertNull(s.setCount)
    }

    @Test
    fun `un type d'activité inconnu retombe sur vélo, un enregistrement incomplet est ignoré`() {
        val json = """{"app":"suivi-sport","data":{"sessions":[{"id":1,"type":"natation","startedAt":5},{"id":2}],""" +
            """"trackPoints":[{"id":1,"sessionId":1,"ts":1}],"muscuSets":[],"settings":[{"key":"k"}]}}"""
        val data = BackupSnapshotCodec.read(StringReader(json)).data!!
        assertEquals(1, data.sessions.size)
        assertEquals(ActivityType.VELO, data.sessions[0].type)
        assertTrue(data.trackPoints.isEmpty())
        assertTrue(data.settings.isEmpty())
    }

    @Test
    fun `data absent ou non objet → null, sans exception`() {
        assertNull(BackupSnapshotCodec.read(StringReader("""{"app":"suivi-sport"}""")).data)
        assertNull(BackupSnapshotCodec.read(StringReader("""{"app":"suivi-sport","data":[1]}""")).data)
        assertNull(BackupSnapshotCodec.read(StringReader("""{"app":"suivi-sport","data":null}""")).data)
    }

    @Test
    fun `JSON invalide → BackupParseException`() {
        assertThrows(BackupParseException::class.java) { BackupSnapshotCodec.read(StringReader("{not json")) }
        assertThrows(BackupParseException::class.java) { BackupSnapshotCodec.read(StringReader("[1,2]")) }
        assertThrows(BackupParseException::class.java) { BackupSnapshotCodec.read(StringReader("")) }
        assertThrows(BackupParseException::class.java) { BackupSnapshotCodec.read(StringReader("""{"app":"suivi-sport","data":{"sessions":[{"id":1""")) }
    }

    @Test
    fun `un instantané vide s'écrit avec ses cinq tableaux`() {
        val empty = DbSnapshot(emptyList(), emptyList(), emptyList(), null, emptyList())
        assertEquals(
            """{"format":1,"app":"suivi-sport","exportedAt":5,"schema":7,"data":{"sessions":[],"trackPoints":[],"muscuSets":[],"bodyMeasurements":[],"settings":[]}}""",
            encode(empty, 5),
        )
    }
}
