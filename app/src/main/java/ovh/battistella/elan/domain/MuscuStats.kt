package ovh.battistella.elan.domain

import kotlin.math.roundToLong

data class StatsSet(val reps: Int, val weightKg: Double, val done: Boolean = false)

data class StatsExercise(val sets: List<StatsSet>)

data class MuscuStats(
    val exerciseCount: Int,
    val totalSets: Int,
    val doneSets: Int,
    val totalVolume: Double,
)

fun muscuStats(exercises: List<StatsExercise>): MuscuStats {
    var totalSets = 0
    var doneSets = 0
    var totalVolume = 0.0
    for (e in exercises) {
        for (s in e.sets) {
            totalSets++
            if (s.done) doneSets++
            totalVolume += s.reps * s.weightKg
        }
    }
    return MuscuStats(exercises.size, totalSets, doneSets, totalVolume)
}

fun muscuSummary(stats: MuscuStats): String =
    "${stats.exerciseCount} exercices · ${stats.totalSets} séries · ${stats.totalVolume.roundToLong()} kg soulevés"
