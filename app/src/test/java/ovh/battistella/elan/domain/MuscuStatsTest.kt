// Tests des agrégats de séance muscu (MuscuStats.kt) : comptage séries,
// séries effectuées, volume soulevé et résumé textuel. Pur.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MuscuStatsTest {
    @Test
    fun `séance vide - tout à zéro`() {
        assertEquals(MuscuStats(exerciseCount = 0, totalSets = 0, doneSets = 0, totalVolume = 0.0), muscuStats(emptyList()))
    }

    @Test
    fun `compte exercices, séries, séries effectuées et volume`() {
        val exercises = listOf(
            StatsExercise(
                listOf(
                    StatsSet(reps = 10, weightKg = 50.0, done = true),
                    StatsSet(reps = 8, weightKg = 50.0, done = true),
                    StatsSet(reps = 8, weightKg = 50.0),
                ),
            ),
            StatsExercise(listOf(StatsSet(reps = 12, weightKg = 20.0, done = false))),
        )
        // volume = 10*50 + 8*50 + 8*50 + 12*20 = 500+400+400+240 = 1540
        assertEquals(MuscuStats(exerciseCount = 2, totalSets = 4, doneSets = 2, totalVolume = 1540.0), muscuStats(exercises))
    }

    @Test
    fun `gère les charges décimales`() {
        val exercises = listOf(StatsExercise(listOf(StatsSet(reps = 10, weightKg = 22.5))))
        assertEquals(225.0, muscuStats(exercises).totalVolume, 1e-9)
    }

    @Test
    fun `muscuSummary formate un résumé lisible avec volume arrondi`() {
        val stats = MuscuStats(exerciseCount = 5, totalSets = 18, doneSets = 18, totalVolume = 2340.6)
        assertEquals("5 exercices · 18 séries · 2341 kg soulevés", muscuSummary(stats))
    }
}
