// Port de __tests__/lib/health-connect.test.ts : construction pure des
// enregistrements Health Connect.
package ovh.battistella.elan.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.HrSample

class HealthRecordsTest {

    private val t0 = 1_780_308_000_000L // 2026-06-01T10:00:00Z
    private val t1 = t0 + 3_600_000L

    private fun types(records: List<HealthRecordSpec>) = records.map { it.type }

    @Test
    fun `vélo complet - ExerciseSession + Distance + ActiveCaloriesBurned + HeartRate`() {
        val records = buildHealthRecords(
            HealthSessionData(
                type = ActivityType.VELO,
                startedAt = t0,
                endedAt = t1,
                distanceM = 25_000.0,
                calories = 600.0,
                hrSamples = listOf(HrSample(t0 + 60_000, 120.0), HrSample(t0 + 120_000, 145.6)),
            ),
        )

        assertEquals(
            listOf(HealthRecordType.EXERCISE_SESSION, HealthRecordType.DISTANCE, HealthRecordType.ACTIVE_CALORIES, HealthRecordType.HEART_RATE),
            types(records),
        )
        val session = records[0] as HealthRecordSpec.ExerciseSession
        assertEquals(8, session.exerciseType)
        assertEquals("Sortie vélo", session.title)
        assertEquals(t0, session.startedAt)
        assertEquals(t1, session.endedAt)

        assertEquals(25_000.0, (records[1] as HealthRecordSpec.Distance).meters, 0.0)
        assertEquals(600.0, (records[2] as HealthRecordSpec.ActiveCalories).kilocalories, 0.0)

        // Les BPM sont arrondis (Health Connect attend des entiers).
        val hr = records[3] as HealthRecordSpec.HeartRate
        assertEquals(listOf(HealthHrPoint(t0 + 60_000, 120), HealthHrPoint(t0 + 120_000, 146)), hr.samples)
    }

    @Test
    fun `muscu sans distance ni FC - ExerciseSession + calories seulement`() {
        val records = buildHealthRecords(HealthSessionData(ActivityType.MUSCU, t0, t1, calories = 250.0))
        assertEquals(listOf(HealthRecordType.EXERCISE_SESSION, HealthRecordType.ACTIVE_CALORIES), types(records))
        assertEquals(70, (records[0] as HealthRecordSpec.ExerciseSession).exerciseType)
    }

    @Test
    fun `ignore distance et calories nulles, absentes ou à zéro`() {
        val records = buildHealthRecords(HealthSessionData(ActivityType.VELO, t0, t1, distanceM = 0.0, calories = null))
        assertEquals(listOf(HealthRecordType.EXERCISE_SESSION), types(records))
    }

    @Test
    fun `écarte les échantillons FC hors intervalle ou aberrants`() {
        val records = buildHealthRecords(
            HealthSessionData(
                type = ActivityType.VELO,
                startedAt = t0,
                endedAt = t1,
                hrSamples = listOf(
                    HrSample(t0 - 1000, 110.0), // avant le départ
                    HrSample(t0 + 1000, 0.0), // bpm nul
                    HrSample(t0 + 2000, 320.0), // bpm aberrant
                    HrSample(t1 + 1000, 130.0), // après l'arrivée
                    HrSample(t0 + 3000, 142.0), // seul valide
                ),
            ),
        )
        val hr = records.filterIsInstance<HealthRecordSpec.HeartRate>().single()
        assertEquals(listOf(HealthHrPoint(t0 + 3000, 142)), hr.samples)
    }

    @Test
    fun `n'émet pas de bloc HeartRate si aucun échantillon valide`() {
        val records = buildHealthRecords(HealthSessionData(ActivityType.VELO, t0, t1, hrSamples = listOf(HrSample(t0 - 1000, 110.0))))
        assertFalse(records.any { it.type == HealthRecordType.HEART_RATE })
    }

    @Test
    fun `retourne une liste vide pour un intervalle invalide`() {
        assertTrue(buildHealthRecords(HealthSessionData(ActivityType.VELO, t1, t0)).isEmpty())
        assertTrue(buildHealthRecords(HealthSessionData(ActivityType.VELO, t0, t0)).isEmpty())
    }

    @Test
    fun `estampille un clientRecordId stable par séance et type - idempotence`() {
        val data = HealthSessionData(ActivityType.VELO, t0, t1, distanceM = 1000.0, calories = 100.0)
        val a = buildHealthRecords(data).map { it.clientRecordId }
        val b = buildHealthRecords(data).map { it.clientRecordId }
        assertEquals(a, b)
        assertEquals(listOf("elan-velo-$t0-session", "elan-velo-$t0-distance", "elan-velo-$t0-calories"), a)

        val other = buildHealthRecords(data.copy(startedAt = t0 + 5000, endedAt = t1 + 5000))
        assertNotEquals(a[0], other[0].clientRecordId)
    }

    @Test
    fun `associe chaque activité à son type d'exercice et son titre`() {
        fun session(type: ActivityType) =
            buildHealthRecords(HealthSessionData(type, t0, t1, distanceM = 8000.0, calories = 500.0))[0] as HealthRecordSpec.ExerciseSession
        assertEquals(8, session(ActivityType.VELO).exerciseType)
        assertEquals(56, session(ActivityType.COURSE).exerciseType)
        assertEquals(79, session(ActivityType.MARCHE).exerciseType)
        assertEquals(70, session(ActivityType.MUSCU).exerciseType)
        assertEquals("Course à pied", session(ActivityType.COURSE).title)
        assertEquals("Marche", session(ActivityType.MARCHE).title)
        assertEquals("Séance musculation", session(ActivityType.MUSCU).title)
        assertEquals("elan-course-$t0-session", session(ActivityType.COURSE).clientRecordId)
    }

    @Test
    fun `healthClientRecordId porte le type, l'instant de début et le rôle`() {
        assertEquals("elan-course-$t0-session", healthClientRecordId(ActivityType.COURSE, t0, "session"))
        assertEquals("elan-velo-$t0-distance", healthClientRecordId(ActivityType.VELO, t0, "distance"))
        // Change avec le type — c'est pourquoi un retype doit supprimer l'ancien miroir.
        assertNotEquals(healthClientRecordId(ActivityType.VELO, t0, "session"), healthClientRecordId(ActivityType.MARCHE, t0, "session"))

        val records = buildHealthRecords(HealthSessionData(ActivityType.MARCHE, t0, t1, distanceM = 5000.0, calories = 200.0))
        assertEquals(
            listOf("session", "distance", "calories").map { healthClientRecordId(ActivityType.MARCHE, t0, it) },
            records.map { it.clientRecordId },
        )
    }

    @Test
    fun `les permissions d'écriture couvrent les quatre types`() {
        assertEquals(
            setOf(
                "android.permission.health.WRITE_EXERCISE",
                "android.permission.health.WRITE_DISTANCE",
                "android.permission.health.WRITE_ACTIVE_CALORIES_BURNED",
                "android.permission.health.WRITE_HEART_RATE",
            ),
            HealthRecordType.WRITE_PERMISSIONS,
        )
    }
}
