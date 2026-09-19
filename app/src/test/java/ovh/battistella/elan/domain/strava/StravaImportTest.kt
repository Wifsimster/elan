package ovh.battistella.elan.domain.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.estimateCalories
import java.time.Instant

/** Port de `__tests__/lib/strava/import.test.ts`, plus les règles de normalisation non couvertes. */
class StravaImportTest {

    private val weightKg = 75.0
    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // GPX minimal valide : 3 points horodatés et géolocalisés sur 20 s.
    private val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata><time>2025-06-02T08:00:00Z</time></metadata>
  <trk><name>Sortie</name><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele>35.0</ele><time>2025-06-02T08:00:00Z</time></trkpt>
    <trkpt lat="48.8570" lon="2.3525"><ele>36.0</ele><time>2025-06-02T08:00:10Z</time></trkpt>
    <trkpt lat="48.8580" lon="2.3530"><ele>40.0</ele><time>2025-06-02T08:00:20Z</time></trkpt>
  </trkseg></trk>
</gpx>"""

    private val gpxAutreDepart = gpx.replace("08:00:", "09:00:")

    private val gpxSansTemps = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele>35.0</ele></trkpt>
    <trkpt lat="48.8570" lon="2.3525"><ele>36.0</ele></trkpt>
  </trkseg></trk>
</gpx>"""

    // Le 2e point saute de ~1000 km en 10 s : vitesse absurde, segment écarté.
    private val gpxTeleport = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><trkseg>
    <trkpt lat="48.8566" lon="2.3522"><ele>35.0</ele><time>2025-06-02T08:00:00Z</time></trkpt>
    <trkpt lat="59.9000" lon="10.7000"><ele>36.0</ele><time>2025-06-02T08:00:10Z</time></trkpt>
    <trkpt lat="59.9001" lon="10.7001"><ele>37.0</ele><time>2025-06-02T08:00:20Z</time></trkpt>
  </trkseg></trk>
</gpx>"""

    @Test
    fun `produit un brouillon vélo depuis un GPX valide`() {
        val (drafts, skipped) = StravaImport.buildDrafts(bytes(gpx), weightKg)
        assertTrue(skipped.isEmpty())
        assertEquals(1, drafts.size)
        val (session, points) = drafts[0]
        assertEquals(ActivityType.VELO, session.type)
        assertEquals("strava", session.source)
        assertEquals("Importé depuis Strava", session.notes)
        assertEquals(ms("2025-06-02T08:00:00Z"), session.startedAt)
        assertEquals(ms("2025-06-02T08:00:20Z"), session.endedAt)
        assertEquals(20, session.durationSec)
        assertEquals(3, points.size)
        assertNull(points[0].speedKmh)
        assertNotNull(points[1].speedKmh)
        assertEquals(36.0, points[1].altitude)
    }

    @Test
    fun `calcule distance, vitesse et dénivelé positif depuis les points GPS`() {
        val session = StravaImport.buildDrafts(bytes(gpx), weightKg).drafts[0].session
        assertNotNull(session.distanceM)
        assertTrue(session.distanceM!! > 0)
        assertTrue(session.avgSpeedKmh!! > 0)
        assertNotNull(session.maxSpeedKmh)
        // Montée 35 → 36 → 40 = +5 m (le bruit < 0,5 m est filtré).
        assertEquals(5.0, session.elevationGainM)
        assertNull(session.avgHr)
        assertNull(session.maxCadence)
        // Calories estimées (pas de calories dans le GPX) sur le temps effectif.
        val effective = session.movingTimeSec ?: session.durationSec
        assertEquals(estimateCalories(ActivityType.VELO, weightKg, effective, session.avgSpeedKmh), session.calories)
    }

    @Test
    fun `segment aberrant — n'inclut pas la distance du saut`() {
        val session = StravaImport.buildDrafts(bytes(gpxTeleport), weightKg).drafts[0].session
        assertTrue(session.distanceM!! < 1000)
        assertTrue(session.maxSpeedKmh!! <= StravaImport.MAX_PLAUSIBLE_SPEED_KMH)
        val points = StravaImport.buildDrafts(bytes(gpxTeleport), weightKg).drafts[0].points
        assertNull("segment écarté : pas de vitesse sur le point téléporté", points[1].speedKmh)
    }

    @Test
    fun `externalId — préfixée strava- et déterministe`() {
        val a = StravaImport.buildDrafts(bytes(gpx), weightKg).drafts[0].session.externalId
        val b = StravaImport.buildDrafts(bytes(gpx), weightKg).drafts[0].session.externalId
        assertTrue(a.startsWith("strava-"))
        assertEquals(7 + 24, a.length)
        assertEquals(a, b)
    }

    @Test
    fun `externalId — diffère pour un départ différent`() {
        val a = StravaImport.buildDrafts(bytes(gpx), weightKg).drafts[0].session.externalId
        val c = StravaImport.buildDrafts(bytes(gpxAutreDepart), weightKg).drafts[0].session.externalId
        assertNotEquals(a, c)
    }

    @Test
    fun `externalId — indépendante du poids`() {
        val a = StravaImport.buildDrafts(bytes(gpx), 60.0).drafts[0].session.externalId
        val b = StravaImport.buildDrafts(bytes(gpx), 95.0).drafts[0].session.externalId
        assertEquals(a, b)
        // Le poids change bien les calories, lui.
        assertNotEquals(
            StravaImport.buildDrafts(bytes(gpx), 60.0).drafts[0].session.calories,
            StravaImport.buildDrafts(bytes(gpx), 95.0).drafts[0].session.calories,
        )
    }

    @Test
    fun `externalId — valeur de référence (formatage JS des composants)`() {
        // sha256("1748851200000|20|<round(distance)>|48.8566|2.3522") : on vérifie
        // que la clé se recalcule à l'identique depuis ses composants.
        val s = StravaImport.buildDrafts(bytes(gpx), weightKg).drafts[0].session
        val expected = "strava-" + sha256Hex("${s.startedAt}|${s.durationSec}|${Math.round(s.distanceM!!)}|48.8566|2.3522").take(24)
        assertEquals(expected, s.externalId)
    }

    @Test
    fun `exclusions — sans horodatage, trop courte, sport non supporté`() {
        val (drafts, skipped) = StravaImport.buildDrafts(bytes(gpxSansTemps), weightKg)
        assertTrue(drafts.isEmpty())
        assertEquals(listOf("aucun horodatage exploitable"), skipped)

        val unPoint = gpx.replace(Regex("<trkpt lat=\"48.85[78].*?</trkpt>\\s*", RegexOption.DOT_MATCHES_ALL), "")
        assertEquals(listOf("séance trop courte ou incomplète"), StravaImport.buildDrafts(bytes(unPoint), weightKg).skipped)

        val natation = GpxTcxParserTest.TCX_SAMPLE.replace("Sport=\"Biking\"", "Sport=\"Swimming\"")
        assertEquals(listOf("activité non supportée ignorée"), StravaImport.buildDrafts(bytes(natation), weightKg).skipped)
    }

    @Test
    fun `TCX — type, distance et calories du fichier, FC et cadence moyennes sans les zéros`() {
        val tcx = GpxTcxParserTest.TCX_SAMPLE
            .replace("Sport=\"Biking\"", "Sport=\"Running\"")
            .replace("<HeartRateBpm><Value>122</Value></HeartRateBpm>", "<HeartRateBpm><Value>0</Value></HeartRateBpm><Cadence>0</Cadence>")
        val s = StravaImport.buildDrafts(bytes(tcx), weightKg).drafts.single().session
        assertEquals(ActivityType.COURSE, s.type)
        assertEquals(12_000.0, s.distanceM)
        assertEquals(320.0, s.calories)
        // Moyennes : les zéros de dropout sont exclus, le max aussi ignore les zéros.
        assertEquals(118.0, s.avgHr)
        assertEquals(118.0, s.maxHr)
        assertEquals(82.0, s.avgCadence)
        assertEquals(82.0, s.maxCadence)
        assertEquals(ms("2025-06-02T08:00:00Z"), s.startedAt)
        assertEquals(10, s.durationSec)
        assertEquals(12_000.0 / 10 * 3.6, s.avgSpeedKmh!!, 1e-9)
    }

    @Test
    fun `marche → marche, GPS (0,0) rejeté, séances sans GPS conservées`() {
        val tcx = GpxTcxParserTest.TCX_SAMPLE
            .replace("Sport=\"Biking\"", "Sport=\"Walking\"")
            .replace("<LatitudeDegrees>48.8570</LatitudeDegrees>", "<LatitudeDegrees>0</LatitudeDegrees>")
            .replace("<LongitudeDegrees>2.3525</LongitudeDegrees>", "<LongitudeDegrees>0</LongitudeDegrees>")
        val d = StravaImport.buildDrafts(bytes(tcx), weightKg).drafts.single()
        assertEquals(ActivityType.MARCHE, d.session.type)
        assertEquals(1, d.points.size)
        // Dénivelé calculé sur tous les points horodatés (35 → 36 = +1).
        assertEquals(1.0, d.session.elevationGainM)
        assertNull(d.session.movingTimeSec)

        val homeTrainer = GpxTcxParserTest.TCX_SAMPLE.replace(Regex("<Position>.*?</Position>", RegexOption.DOT_MATCHES_ALL), "")
        val ht = StravaImport.buildDrafts(bytes(homeTrainer), weightKg).drafts.single()
        assertTrue(ht.points.isEmpty())
        assertEquals(12_000.0, ht.session.distanceM)
        assertTrue(ht.session.externalId.startsWith("strava-"))
    }

    @Test
    fun `avgMax et computeGain`() {
        assertEquals(null to null, StravaImport.avgMax(listOf(null, 0.0, -1.0)))
        assertEquals(101.0 to 120.0, StravaImport.avgMax(listOf(82.0, 120.0, null, 0.0)))
        assertNull(StravaImport.computeGain(listOf(ParsedPoint(1, null, null, null, null, null))))
        val pts = listOf(10.0, 10.4, 12.0, 11.0, 13.6).map { ParsedPoint(1, null, null, it, null, null) }
        // +0.4 (bruit) ignoré, +1.6 crédité, -1 ignoré, +2.6 crédité → 4.2 → 4.
        assertEquals(4.0, StravaImport.computeGain(pts))
        assertTrue(StravaImport.isValidLatLon(48.0, 2.0))
        assertTrue(!StravaImport.isValidLatLon(0.0, 0.0))
        assertTrue(!StravaImport.isValidLatLon(91.0, 0.0))
        assertTrue(!StravaImport.isValidLatLon(null, 1.0))
    }

    private fun sha256Hex(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
