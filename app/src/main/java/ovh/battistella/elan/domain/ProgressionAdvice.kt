package ovh.battistella.elan.domain

enum class ProgressionAdvice(val key: String) {
    AUGMENTE("augmente"),
    MAINTIENS("maintiens"),
    REDUIS("reduis"),
}

fun difficultyScore(d: Difficulty?): Int? = when (d) {
    Difficulty.FACILE -> 1
    Difficulty.MOYEN -> 2
    Difficulty.DUR -> 3
    null -> null
}

fun difficultyLabel(d: Difficulty): String = when (d) {
    Difficulty.FACILE -> "Facile"
    Difficulty.MOYEN -> "Moyen"
    Difficulty.DUR -> "Dur"
}

fun suggestProgression(recent: List<Difficulty?>, window: Int = 3): ProgressionAdvice {
    val scored = recent.mapNotNull(::difficultyScore)
    if (scored.isEmpty()) return ProgressionAdvice.MAINTIENS

    val lastN = if (window <= 0) scored else scored.takeLast(window)
    val last = lastN.last()
    val avg = lastN.sum().toDouble() / lastN.size

    if (last == 3) return ProgressionAdvice.REDUIS
    if (last == 1 && 3 !in lastN) return ProgressionAdvice.AUGMENTE
    if (avg <= 1.5) return ProgressionAdvice.AUGMENTE
    if (avg >= 2.5) return ProgressionAdvice.REDUIS
    return ProgressionAdvice.MAINTIENS
}

fun adviceLabel(a: ProgressionAdvice): String = when (a) {
    ProgressionAdvice.AUGMENTE -> "Augmente les reps ou la charge"
    ProgressionAdvice.REDUIS -> "Réduis la charge, soigne la technique"
    ProgressionAdvice.MAINTIENS -> "Maintiens, charge bien calibrée"
}
