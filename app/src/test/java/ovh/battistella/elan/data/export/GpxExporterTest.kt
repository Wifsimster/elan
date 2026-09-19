package ovh.battistella.elan.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.strava.GpxTcxParser
import ovh.battistella.elan.domain.strava.ParsedSport
import ovh.battistella.elan.domain.strava.StravaFormat
import ovh.battistella.elan.testing.TestSupport
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class GpxExporterTest {

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
        FileShare.purgeShared(context)
    }

    private fun localMs(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(startedAt: Long = localMs(2026, 6, 9, 14, 30), type: ActivityType = ActivityType.VELO) = Session(
        id = 1, type = type, startedAt = startedAt, endedAt = startedAt + 60_000, durationSec = 60,
        movingTimeSec = null, notes = null, avgHr = null, maxHr = null, distanceM = null, avgSpeedKmh = null,
        maxSpeedKmh = null, elevationGainM = null, avgCadence = null, maxCadence = null, calories = null,
        source = null, externalId = null,
    )

    private fun points(startedAt: Long) = listOf(
        TrackPoint(1, 1, startedAt, 48.8566, 2.3522, 35.04, 18.0, 120.0, null),
        TrackPoint(2, 1, startedAt + 10_000, 48.85700001234, 2.3525, null, 20.0, 0.0, 84.6),
        TrackPoint(3, 1, startedAt + 20_000, 48.858, 2.353, 37.25, 22.0, null, null),
    )

    @Test
    fun `nom, fichier et échappement`() {
        assertEquals("Sortie vélo — Après-midi", rideName(session()))
        assertEquals("Sortie vélo — Nuit", rideName(session(localMs(2026, 1, 1, 5, 59))))
        assertEquals("Sortie vélo — Matin", rideName(session(localMs(2026, 1, 1, 6, 0))))
        assertEquals("Sortie vélo — Soir", rideName(session(localMs(2026, 1, 1, 18, 0))))
        assertEquals("elan-velo-2026-06-09-1430.gpx", rideFileName(session()))
        assertEquals("a&lt;b&gt;c&amp;d&apos;e&quot;f", escapeXml("a<b>c&d'e\"f"))
    }

    @Test
    fun `course et marche — nom, fichier et type GPX suivent l'activité, relus à l'import`() {
        val course = session(type = ActivityType.COURSE)
        val marche = session(localMs(2026, 1, 1, 6, 0), ActivityType.MARCHE)
        assertEquals("Course à pied — Après-midi", rideName(course))
        assertEquals("Marche — Matin", rideName(marche))
        assertEquals("elan-course-2026-06-09-1430.gpx", rideFileName(course))
        assertEquals("elan-marche-2026-01-01-0600.gpx", rideFileName(marche))

        fun typeOf(s: Session) = Regex("<type>(\\w+)</type>").find(buildRideGpx(s, points(s.startedAt)))!!.groupValues[1]
        assertEquals("cycling", typeOf(session()))
        assertEquals("running", typeOf(course))
        assertEquals("walking", typeOf(marche))

        fun sportOf(s: Session) = GpxTcxParser.parse(buildRideGpx(s, points(s.startedAt))).activities.single().sport
        assertEquals(ParsedSport.CYCLING, sportOf(session()))
        assertEquals(ParsedSport.RUNNING, sportOf(course))
        assertEquals(ParsedSport.WALKING, sportOf(marche))
    }

    @Test
    fun `buildRideGpx émet un GPX 1 1 fidèle à l'app d'origine`() {
        val s = session(1_749_479_400_000L) // 2025-06-09T14:30:00Z
        val gpx = buildRideGpx(s, points(s.startedAt))
        val expectedHead = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Élan" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
  <metadata>
    <time>2025-06-09T14:30:00.000Z</time>
  </metadata>
  <trk>
    <name>${escapeXml(rideName(s))}</name>
    <type>cycling</type>
    <trkseg>
      <trkpt lat="48.8566" lon="2.3522">
        <ele>35</ele>
        <time>2025-06-09T14:30:00.000Z</time>
        <extensions>
          <gpxtpx:TrackPointExtension>
            <gpxtpx:hr>120</gpxtpx:hr>
          </gpxtpx:TrackPointExtension>
        </extensions>
      </trkpt>
      <trkpt lat="48.857" lon="2.3525">
        <time>2025-06-09T14:30:10.000Z</time>
        <extensions>
          <gpxtpx:TrackPointExtension>
            <gpxtpx:cad>85</gpxtpx:cad>
          </gpxtpx:TrackPointExtension>
        </extensions>
      </trkpt>
      <trkpt lat="48.858" lon="2.353">
        <ele>37.3</ele>
        <time>2025-06-09T14:30:20.000Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>"""
        assertEquals(expectedHead, gpx)
    }

    @Test
    fun `export → parse → mêmes points`() {
        val s = session()
        val pts = points(s.startedAt)
        val r = GpxTcxParser.parse(buildRideGpx(s, pts))
        assertEquals(StravaFormat.GPX, r.format)
        val a = r.activities.single()
        assertEquals(s.startedAt, a.startedAt)
        assertEquals(3, a.points.size)
        for (i in pts.indices) {
            assertEquals(pts[i].ts, a.points[i].ts)
            assertEquals(pts[i].lat, a.points[i].lat!!, 1e-7)
            assertEquals(pts[i].lon, a.points[i].lon!!, 1e-7)
        }
        assertEquals(120.0, a.points[0].hr)
        assertNull("hr 0 = dropout, non émis", a.points[1].hr)
        assertEquals(85.0, a.points[1].cad)
        assertEquals(37.3, a.points[2].ele)
    }

    @Test
    fun `exportSession applique la zone de confidentialité et refuse un tracé trop court`() = runTest {
        val id = db.sessionDao().insert(TestSupport.session())
        val exporter = GpxExporter(repos.sessions)
        assertNull(exporter.exportSession(context, id, 0.0))
        assertNull(exporter.exportSession(context, 999, 0.0))

        val t0 = 1_700_000_000_000L
        // Départ / arrivée au « domicile », deux points loin au milieu.
        repos.sessions.insertTrackPoints(
            id,
            listOf(
                TestSupport.trackPointInput(ts = t0, lat = 48.8566, lon = 2.3522),
                TestSupport.trackPointInput(ts = t0 + 1000, lat = 48.8567, lon = 2.3523),
                TestSupport.trackPointInput(ts = t0 + 2000, lat = 48.8700, lon = 2.3700),
                TestSupport.trackPointInput(ts = t0 + 3000, lat = 48.8701, lon = 2.3701),
                TestSupport.trackPointInput(ts = t0 + 4000, lat = 48.8566, lon = 2.3522),
            ),
        )

        val full = exporter.exportSession(context, id, 0.0)!!
        assertTrue(full.exists())
        assertTrue(full.parentFile == FileShare.shareDir(context))
        assertEquals(5, GpxTcxParser.parse(full.readText()).activities[0].points.size)

        val trimmed = exporter.exportSession(context, id, 200.0)!!
        val pts = GpxTcxParser.parse(trimmed.readText()).activities[0].points
        assertEquals(2, pts.size)
        assertEquals(48.87, pts[0].lat!!, 1e-6)

        FileShare.purgeShared(context)
        assertFalse(trimmed.exists())
    }
}
