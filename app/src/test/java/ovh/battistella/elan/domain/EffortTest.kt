// Tests de l'indicateur d'effort : zone cardio quand une FC existe, durée sinon.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EffortTest {
    private fun session(avgHr: Double? = null, durationSec: Int = 0, movingTimeSec: Int? = null) = Session(
        id = 1, type = ActivityType.VELO, startedAt = 0, endedAt = 1, durationSec = durationSec,
        movingTimeSec = movingTimeSec, notes = null, avgHr = avgHr, maxHr = null, distanceM = null,
        avgSpeedKmh = null, maxSpeedKmh = null, elevationGainM = null, avgCadence = null, maxCadence = null,
        calories = null, source = null, externalId = null,
    )

    @Test
    fun `avec FC moyenne, le niveau est la zone cardio`() {
        assertEquals(1, sessionEffort(session(avgHr = 100.0), 200.0).level)
        assertEquals(2, sessionEffort(session(avgHr = 130.0), 200.0).level)
        assertEquals(3, sessionEffort(session(avgHr = 150.0), 200.0).level)
        assertEquals(4, sessionEffort(session(avgHr = 170.0), 200.0).level)
        assertEquals(5, sessionEffort(session(avgHr = 190.0), 200.0).level)
    }

    @Test
    fun `sans FC, le niveau suit la durée par paliers 30, 60, 90, 150 min`() {
        assertEquals(1, sessionEffort(session(durationSec = 29 * 60), 190.0).level)
        assertEquals(2, sessionEffort(session(durationSec = 30 * 60), 190.0).level)
        assertEquals(3, sessionEffort(session(durationSec = 60 * 60), 190.0).level)
        assertEquals(4, sessionEffort(session(durationSec = 90 * 60), 190.0).level)
        assertEquals(5, sessionEffort(session(durationSec = 150 * 60), 190.0).level)
    }

    @Test
    fun `sans FC, le temps en mouvement prime sur la durée totale`() {
        val s = session(durationSec = 3 * 3600, movingTimeSec = 20 * 60)
        assertEquals(1, sessionEffort(s, 190.0).level)
    }

    @Test
    fun `FC max inconnue (0) → repli sur la durée même avec une FC moyenne`() {
        assertEquals(5, sessionEffort(session(avgHr = 100.0, durationSec = 4 * 3600), 0.0).level)
    }

    @Test
    fun `libellés et couleurs par niveau`() {
        val hr = listOf(100.0, 130.0, 150.0, 170.0, 190.0)
        val efforts = hr.map { sessionEffort(session(avgHr = it), 200.0) }
        assertEquals(listOf("Facile", "Modéré", "Soutenu", "Difficile", "Maximal"), efforts.map { it.label })
        assertEquals(listOf("success", "success", "warning", "warning", "heart"), efforts.map { it.colorKey })
    }
}
