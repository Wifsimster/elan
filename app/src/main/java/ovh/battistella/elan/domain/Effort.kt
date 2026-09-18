// Indicateur d'effort d'une séance, façon Strava (« Facile » → « Maximal »).
// S'appuie sur la zone cardio moyenne si une FC est disponible, sinon retombe
// sur la durée. Estimation indicative, pas une mesure physiologique.
package ovh.battistella.elan.domain

data class Effort(
    /** Niveau de 1 à 5. */
    val level: Int,
    val label: String,
    /** Clé de couleur du thème : `success`, `warning` ou `heart`. */
    val colorKey: String,
)

private val LABELS = mapOf(1 to "Facile", 2 to "Modéré", 3 to "Soutenu", 4 to "Difficile", 5 to "Maximal")

// Vert pour le facile, ambre pour le soutenu, rose cardio pour le maximal.
private val COLORS = mapOf(1 to "success", 2 to "success", 3 to "warning", 4 to "warning", 5 to "heart")

fun sessionEffort(session: Session, maxHr: Double): Effort {
    val avgHr = session.avgHr
    val level = if (avgHr != null && maxHr > 0) {
        heartRateZone(avgHr, maxHr)
    } else {
        // Hors FC, l'effort suit la durée : temps en mouvement quand il est connu,
        // pour qu'un long arrêt ne le surévalue pas.
        val minutes = (session.movingTimeSec ?: session.durationSec) / 60.0
        when {
            minutes < 30 -> 1
            minutes < 60 -> 2
            minutes < 90 -> 3
            minutes < 150 -> 4
            else -> 5
        }
    }
    return Effort(level = level, label = LABELS.getValue(level), colorKey = COLORS.getValue(level))
}
