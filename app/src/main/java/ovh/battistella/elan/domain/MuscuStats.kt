// Agrégats d'une séance de musculation en cours : nombre de séries, séries
// effectuées et volume soulevé. Pur (aucune dépendance Android) — extrait de
// l'écran muscu pour être testable et réutilisable.
package ovh.battistella.elan.domain

import kotlin.math.roundToLong

/** Une série telle que consommée par les agrégats. */
data class StatsSet(val reps: Int, val weightKg: Double, val done: Boolean = false)

/** Forme minimale d'un exercice consommée par les agrégats (une liste de séries). */
data class StatsExercise(val sets: List<StatsSet>)

data class MuscuStats(
    /** Nombre d'exercices de la séance. */
    val exerciseCount: Int,
    /** Nombre total de séries (toutes confondues). */
    val totalSets: Int,
    /** Séries cochées comme effectuées. */
    val doneSets: Int,
    /** Volume soulevé en kg : Σ reps × charge. */
    val totalVolume: Double,
)

/** Calcule les agrégats d'une séance muscu en un seul parcours. */
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

/** Résumé textuel d'une séance, stocké dans les notes (« 5 exercices · 18 séries · 2341 kg soulevés »). */
fun muscuSummary(stats: MuscuStats): String =
    "${stats.exerciseCount} exercices · ${stats.totalSets} séries · ${stats.totalVolume.roundToLong()} kg soulevés"
