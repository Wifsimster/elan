package ovh.battistella.elan.domain.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Port de `__tests__/lib/strava/parse.test.ts`. */
class GpxTcxParserTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `GPX — détecte le format et renvoie une seule activité`() {
        val r = GpxTcxParser.parse(GPX_SAMPLE)
        assertEquals(StravaFormat.GPX, r.format)
        assertEquals(1, r.activities.size)
    }

    @Test
    fun `GPX — extrait les trackpoints avec lat, lon, ele`() {
        val a = GpxTcxParser.parse(GPX_SAMPLE).activities[0]
        assertEquals(3, a.points.size)
        assertEquals(48.8566, a.points[0].lat!!, 1e-4)
        assertEquals(2.3522, a.points[0].lon!!, 1e-4)
        assertEquals(35.0, a.points[0].ele)
        assertEquals(ms("2025-06-02T08:00:10Z"), a.points[1].ts)
    }

    @Test
    fun `GPX — extrait FC et cadence des extensions, null sans extension`() {
        val a = GpxTcxParser.parse(GPX_SAMPLE).activities[0]
        assertEquals(120.0, a.points[0].hr)
        assertEquals(80.0, a.points[0].cad)
        assertNull(a.points[1].hr)
        assertNull(a.points[1].cad)
    }

    @Test
    fun `GPX — une balise vide vaut absente (null, pas 0)`() {
        val gpx = """<?xml version="1.0"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele></ele><time>2025-06-02T08:00:00Z</time></trkpt>
  </trkseg></trk>
</gpx>"""
        val a = GpxTcxParser.parse(gpx).activities[0]
        assertNull(a.points[0].ele)
        assertEquals(ms("2025-06-02T08:00:00Z"), a.startedAt)
    }

    @Test
    fun `GPX — la métadonnée time donne le début, sport inconnu, ni distance ni calories`() {
        val a = GpxTcxParser.parse(GPX_SAMPLE).activities[0]
        assertEquals(ms("2025-06-02T08:00:00Z"), a.startedAt)
        assertEquals(ParsedSport.UNKNOWN, a.sport)
        assertNull(a.distanceM)
        assertNull(a.calories)
    }

    @Test
    fun `GPX — le type de la trace est relu, toute valeur inconnue reste indéterminée`() {
        fun sport(type: String?): ParsedSport {
            val trk = if (type == null) "<trk>" else "<trk><name>x</name><type>$type</type>"
            val gpx = """<?xml version="1.0"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1">
  $trk<trkseg>
    <trkpt lat="48.8566" lon="2.3522"><time>2025-06-02T08:00:00Z</time><type>running</type></trkpt>
  </trkseg></trk>
</gpx>"""
            return GpxTcxParser.parse(gpx).activities[0].sport
        }
        assertEquals(ParsedSport.CYCLING, sport("cycling"))
        assertEquals(ParsedSport.CYCLING, sport(" Biking "))
        assertEquals(ParsedSport.RUNNING, sport("running"))
        assertEquals(ParsedSport.WALKING, sport("walking"))
        assertEquals(ParsedSport.WALKING, sport("hiking"))
        // Codes Garmin, sports non couverts, absence : jamais OTHER (importé en vélo, comme l'app d'origine).
        assertEquals(ParsedSport.UNKNOWN, sport("9"))
        assertEquals(ParsedSport.UNKNOWN, sport("swimming"))
        assertEquals(ParsedSport.UNKNOWN, sport(""))
        assertEquals("le <type> d'un trkpt n'est pas celui de la trace", ParsedSport.UNKNOWN, sport(null))
    }

    @Test
    fun `GPX — préfixes de namespace et casse ignorés`() {
        val gpx = """<?xml version="1.0"?>
<gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:g="urn:g" xmlns:x="urn:x">
  <trk><g:trkseg>
    <g:trkpt LAT="1.5" Lon="2.5"><g:ele>10</g:ele><g:time>2025-06-02T08:00:00.500+02:00</g:time>
      <extensions><x:hr>99</x:hr><x:cad>0</x:cad></extensions></g:trkpt>
  </g:trkseg></trk>
</gpx>"""
        val a = GpxTcxParser.parse(gpx).activities[0]
        assertEquals(1.5, a.points[0].lat)
        assertEquals(2.5, a.points[0].lon)
        assertEquals(99.0, a.points[0].hr)
        assertEquals(0.0, a.points[0].cad)
        assertEquals(ms("2025-06-02T06:00:00.500Z"), a.points[0].ts)
    }

    @Test
    fun `TCX — détecte le format, le sport Biking et somme les laps`() {
        val r = GpxTcxParser.parse(TCX_SAMPLE)
        assertEquals(StravaFormat.TCX, r.format)
        assertEquals(1, r.activities.size)
        val a = r.activities[0]
        assertEquals(ParsedSport.CYCLING, a.sport)
        assertEquals(12_000.0, a.distanceM)
        assertEquals(320.0, a.calories)
    }

    @Test
    fun `TCX — somme distance et calories sur PLUSIEURS laps`() {
        val twoLaps = TCX_SAMPLE.replace(
            "</Lap>",
            "</Lap><Lap StartTime=\"2025-06-02T08:30:00Z\"><DistanceMeters>3000</DistanceMeters><Calories>80</Calories><Track/></Lap>",
        )
        val a = GpxTcxParser.parse(twoLaps).activities[0]
        assertEquals(15_000.0, a.distanceM)
        assertEquals(400.0, a.calories)
    }

    @Test
    fun `TCX — extrait les trackpoints avec position et FC`() {
        val a = GpxTcxParser.parse(TCX_SAMPLE).activities[0]
        assertEquals(2, a.points.size)
        assertEquals(48.8566, a.points[0].lat!!, 1e-4)
        assertEquals(2.3522, a.points[0].lon!!, 1e-4)
        assertEquals(35.0, a.points[0].ele)
        assertEquals(118.0, a.points[0].hr)
        assertEquals(82.0, a.points[0].cad)
        assertEquals(122.0, a.points[1].hr)
        assertNull(a.points[1].cad)
        assertEquals(ms("2025-06-02T08:00:10Z"), a.points[1].ts)
    }

    @Test
    fun `TCX — startedAt vient de Id, sinon du premier point`() {
        assertEquals(ms("2025-06-02T08:00:00Z"), GpxTcxParser.parse(TCX_SAMPLE).activities[0].startedAt)
        val sansId = TCX_SAMPLE.replace("<Id>2025-06-02T08:00:00Z</Id>", "")
        assertEquals(ms("2025-06-02T08:00:00Z"), GpxTcxParser.parse(sansId).activities[0].startedAt)
    }

    @Test
    fun `TCX — un fichier peut contenir plusieurs activités`() {
        val two = TCX_SAMPLE.replace("</Activities>", "<Activity Sport=\"Running\"><Id>2025-06-03T08:00:00Z</Id></Activity></Activities>")
        val r = GpxTcxParser.parse(two)
        assertEquals(2, r.activities.size)
        assertEquals(ParsedSport.RUNNING, r.activities[1].sport)
        assertEquals(ms("2025-06-03T08:00:00Z"), r.activities[1].startedAt)
        assertTrue(r.activities[1].points.isEmpty())
        assertNull(r.activities[1].distanceM)
    }

    @Test
    fun `sécurité — rejette une DOCTYPE (XXE, billion laughs)`() {
        val malicious = """<?xml version="1.0"?>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
<gpx></gpx>"""
        val e = assertThrows(StravaFileException::class.java) { GpxTcxParser.parse(malicious) }
        assertTrue(e.message!!.contains("DOCTYPE/ENTITY"))
        assertThrows(StravaFileException::class.java) { GpxTcxParser.parse("<gpx><!entity x 'y'></gpx>") }
    }

    @Test
    fun `sécurité — format inconnu, fichier trop gros, XML mal formé`() {
        assertEquals("Format non reconnu (ni GPX ni TCX).", assertThrows(StravaFileException::class.java) { GpxTcxParser.parse("<html></html>") }.message)
        assertEquals("Fichier trop volumineux.", assertThrows(StravaFileException::class.java) {
            GpxTcxParser.parse(CharArray(GpxTcxParser.MAX_CHARS + 1) { ' ' }.concatToString())
        }.message)
        assertEquals("Fichier XML illisible.", assertThrows(StravaFileException::class.java) { GpxTcxParser.parse("<gpx><trk>") }.message)
    }

    @Test
    fun `détection du sport TCX`() {
        fun sport(s: String) = GpxTcxParser.parse(TCX_SAMPLE.replace("Sport=\"Biking\"", "Sport=\"$s\"")).activities[0].sport
        assertEquals(ParsedSport.RUNNING, sport("Running"))
        assertEquals(ParsedSport.RUNNING, sport("Course à pied"))
        assertEquals(ParsedSport.WALKING, sport("Walking"))
        assertEquals(ParsedSport.WALKING, sport("Hiking"))
        assertEquals(ParsedSport.WALKING, sport("Marche"))
        assertEquals(ParsedSport.CYCLING, sport("Cycling"))
        assertEquals(ParsedSport.CYCLING, sport("Biking"))
        assertEquals(ParsedSport.OTHER, sport("Swimming"))
        assertEquals(ParsedSport.UNKNOWN, sport(""))
    }

    @Test
    fun `num et parseTime suivent Number et Date parse`() {
        assertNull(GpxTcxParser.num(null))
        assertNull(GpxTcxParser.num(""))
        assertNull(GpxTcxParser.num("  "))
        assertNull(GpxTcxParser.num("abc"))
        assertNull(GpxTcxParser.num("12d"))
        assertNull(GpxTcxParser.num("Infinity"))
        assertEquals(12.5, GpxTcxParser.num(" 12.5 "))
        assertEquals(1000.0, GpxTcxParser.num("1e3"))
        assertEquals(-3.0, GpxTcxParser.num("-3"))
        assertNull(GpxTcxParser.parseTime(""))
        assertNull(GpxTcxParser.parseTime("hier"))
        assertEquals(ms("2025-06-02T08:00:00Z"), GpxTcxParser.parseTime(" 2025-06-02T08:00:00Z "))
        assertEquals(ms("2025-06-02T06:00:00Z"), GpxTcxParser.parseTime("2025-06-02T08:00:00+0200"))
        assertEquals(ms("2025-06-02T00:00:00Z"), GpxTcxParser.parseTime("2025-06-02"))
    }

    companion object {
        val GPX_SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <time>2025-06-02T08:00:00Z</time>
  </metadata>
  <trk>
    <name>Sortie matinale</name>
    <trkseg>
      <trkpt lat="48.8566" lon="2.3522">
        <ele>35.0</ele>
        <time>2025-06-02T08:00:00Z</time>
        <extensions>
          <gpxtpx:TrackPointExtension xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
            <gpxtpx:hr>120</gpxtpx:hr>
            <gpxtpx:cad>80</gpxtpx:cad>
          </gpxtpx:TrackPointExtension>
        </extensions>
      </trkpt>
      <trkpt lat="48.8570" lon="2.3525">
        <ele>36.0</ele>
        <time>2025-06-02T08:00:10Z</time>
      </trkpt>
      <trkpt lat="48.8580" lon="2.3530">
        <ele>37.0</ele>
        <time>2025-06-02T08:00:20Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>"""

        val TCX_SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
  <Activities>
    <Activity Sport="Biking">
      <Id>2025-06-02T08:00:00Z</Id>
      <Lap StartTime="2025-06-02T08:00:00Z">
        <TotalTimeSeconds>1800</TotalTimeSeconds>
        <DistanceMeters>12000</DistanceMeters>
        <Calories>320</Calories>
        <Track>
          <Trackpoint>
            <Time>2025-06-02T08:00:00Z</Time>
            <Position>
              <LatitudeDegrees>48.8566</LatitudeDegrees>
              <LongitudeDegrees>2.3522</LongitudeDegrees>
            </Position>
            <AltitudeMeters>35.0</AltitudeMeters>
            <DistanceMeters>0</DistanceMeters>
            <HeartRateBpm><Value>118</Value></HeartRateBpm>
            <Cadence>82</Cadence>
          </Trackpoint>
          <Trackpoint>
            <Time>2025-06-02T08:00:10Z</Time>
            <Position>
              <LatitudeDegrees>48.8570</LatitudeDegrees>
              <LongitudeDegrees>2.3525</LongitudeDegrees>
            </Position>
            <AltitudeMeters>36.0</AltitudeMeters>
            <HeartRateBpm><Value>122</Value></HeartRateBpm>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>"""
    }
}
