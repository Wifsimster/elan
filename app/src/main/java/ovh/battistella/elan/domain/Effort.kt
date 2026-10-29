package ovh.battistella.elan.domain

data class Effort(
    val level: Int,
    val label: String,
    val colorKey: String,
)

private val LABELS = mapOf(1 to "Facile", 2 to "Modéré", 3 to "Soutenu", 4 to "Difficile", 5 to "Maximal")

private val COLORS = mapOf(1 to "success", 2 to "success", 3 to "warning", 4 to "warning", 5 to "heart")

fun sessionEffort(session: Session, maxHr: Double): Effort {
    val avgHr = session.avgHr
    val level = if (avgHr != null && maxHr > 0) {
        heartRateZone(avgHr, maxHr)
    } else {
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
