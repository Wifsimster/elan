// Tests du changement de type d'une séance enregistrée : décider si le
// changement est permis, et recalculer ce qui dépend du type.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRetypeTest {
    private val profile = Profile(weightKg = 70.0, maxHr = 190.0)

    /** Sortie d'une heure, 20 km, 20 km/h de moyenne — de quoi comparer les barèmes. */
    private fun session(
        type: ActivityType = ActivityType.VELO,
        durationSec: Int = 3600,
        movingTimeSec: Int? = 3600,
        avgHr: Double? = null,
        maxHr: Double? = null,
        avgCadence: Double? = 85.0,
        maxCadence: Double? = 110.0,
        calories: Double? = 500.0,
    ) = Session(
        id = 1,
        type = type,
        startedAt = 1_700_000_000_000,
        endedAt = 1_700_003_600_000,
        durationSec = durationSec,
        movingTimeSec = movingTimeSec,
        notes = null,
        avgHr = avgHr,
        maxHr = maxHr,
        distanceM = 20_000.0,
        avgSpeedKmh = 20.0,
        maxSpeedKmh = 30.0,
        elevationGainM = 100.0,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        calories = calories,
        source = null,
        externalId = null,
    )

    @Test
    fun `canRetype autorise les échanges entre activités tracées`() {
        assertTrue(canRetype(ActivityType.VELO, ActivityType.MARCHE))
        assertTrue(canRetype(ActivityType.VELO, ActivityType.COURSE))
        assertTrue(canRetype(ActivityType.MARCHE, ActivityType.COURSE))
        assertTrue(canRetype(ActivityType.COURSE, ActivityType.VELO))
    }

    @Test
    fun `canRetype refuse la musculation dans les deux sens`() {
        assertFalse(canRetype(ActivityType.MUSCU, ActivityType.VELO))
        assertFalse(canRetype(ActivityType.VELO, ActivityType.MUSCU))
        assertFalse(canRetype(ActivityType.MUSCU, ActivityType.COURSE))
    }

    @Test
    fun `canRetype refuse un changement vers le type courant`() {
        for (t in ActivityType.entries) assertFalse(canRetype(t, t))
    }

    @Test
    fun `renvoie null quand le changement n'est pas permis`() {
        assertNull(retypeChanges(session(), ActivityType.VELO, profile))
        assertNull(retypeChanges(session(), ActivityType.MUSCU, profile))
        assertNull(retypeChanges(session(type = ActivityType.MUSCU), ActivityType.VELO, profile))
    }

    @Test
    fun `porte le nouveau type`() {
        assertEquals(ActivityType.MARCHE, retypeChanges(session(), ActivityType.MARCHE, profile)!!.type)
    }

    @Test
    fun `ré-estime les calories avec le barème de la nouvelle activité`() {
        val s = session()
        val versCourse = retypeChanges(s, ActivityType.COURSE, profile)!!
        val versMarche = retypeChanges(s, ActivityType.MARCHE, profile)!!
        // À 20 km/h : courir coûte beaucoup plus que pédaler, marcher (extrapolation
        // plate au-delà de la table) reste au-dessus du vélo mais loin de la course.
        assertTrue(versCourse.calories > s.calories!!)
        assertTrue(versCourse.calories > versMarche.calories)
        // La valeur stockée d'origine n'entre jamais dans le nouveau calcul.
        val memeSeanceAutreCalories = session(calories = 99_999.0)
        assertEquals(versCourse.calories, retypeChanges(memeSeanceAutreCalories, ActivityType.COURSE, profile)!!.calories, 1e-5)
    }

    @Test
    fun `efface la cadence dès qu'on passe à pied`() {
        for (to in listOf(ActivityType.COURSE, ActivityType.MARCHE)) {
            val changes = retypeChanges(session(), to, profile)!!
            assertNull(changes.avgCadence)
            assertNull(changes.maxCadence)
        }
    }

    @Test
    fun `conserve la cadence en revenant au vélo`() {
        val s = session(type = ActivityType.COURSE, avgCadence = 80.0, maxCadence = 95.0)
        val changes = retypeChanges(s, ActivityType.VELO, profile)!!
        assertEquals(80.0, changes.avgCadence!!, 0.0)
        assertEquals(95.0, changes.maxCadence!!, 0.0)
    }

    @Test
    fun `compte le temps EN MOUVEMENT quand il est connu`() {
        val avecArrets = session(durationSec = 7200, movingTimeSec = 3600)
        val sansArrets = session(durationSec = 3600, movingTimeSec = null)
        assertEquals(
            retypeChanges(sansArrets, ActivityType.COURSE, profile)!!.calories,
            retypeChanges(avecArrets, ActivityType.COURSE, profile)!!.calories,
            1e-5,
        )
    }

    @Test
    fun `retombe sur la durée totale sans temps en mouvement`() {
        val court = session(durationSec = 1800, movingTimeSec = null)
        val long = session(durationSec = 3600, movingTimeSec = null)
        assertTrue(
            retypeChanges(long, ActivityType.COURSE, profile)!!.calories >
                retypeChanges(court, ActivityType.COURSE, profile)!!.calories,
        )
    }

    @Test
    fun `utilise la FC max du PROFIL, pas celle de la séance`() {
        val a = session(avgHr = 150.0, maxHr = 175.0)
        val b = session(avgHr = 150.0, maxHr = 200.0)
        assertEquals(
            retypeChanges(b, ActivityType.COURSE, profile)!!.calories,
            retypeChanges(a, ActivityType.COURSE, profile)!!.calories,
            1e-5,
        )

        val profilPlusHaut = Profile(weightKg = 70.0, maxHr = 210.0)
        assertNotEquals(
            retypeChanges(a, ActivityType.COURSE, profile)!!.calories,
            retypeChanges(a, ActivityType.COURSE, profilPlusHaut)!!.calories,
            1e-5,
        )
    }

    @Test
    fun `tient compte du poids du profil`() {
        val leger = retypeChanges(session(), ActivityType.COURSE, Profile(weightKg = 55.0, maxHr = 190.0))!!
        val lourd = retypeChanges(session(), ActivityType.COURSE, Profile(weightKg = 95.0, maxHr = 190.0))!!
        assertTrue(lourd.calories > leger.calories)
    }
}
