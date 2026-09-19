// Conseil de progression en musculation à partir du ressenti noté séance après
// séance (facile / moyen / dur). Fonction pure, 100 % local, sans dépendance :
// c'est le seul endroit où vit la logique « dois-je augmenter les reps/la charge ? ».
package ovh.battistella.elan.domain

enum class ProgressionAdvice(val key: String) {
    AUGMENTE("augmente"),
    MAINTIENS("maintiens"),
    REDUIS("reduis"),
}

/** Score ordonnable d'un ressenti (`null`/non noté = ignoré dans les calculs). */
fun difficultyScore(d: Difficulty?): Int? = when (d) {
    Difficulty.FACILE -> 1
    Difficulty.MOYEN -> 2
    Difficulty.DUR -> 3
    null -> null
}

/** Libellé FR d'un ressenti, pour l'affichage. */
fun difficultyLabel(d: Difficulty): String = when (d) {
    Difficulty.FACILE -> "Facile"
    Difficulty.MOYEN -> "Moyen"
    Difficulty.DUR -> "Dur"
}

/**
 * Conseille d'augmenter, maintenir ou réduire la charge/les reps d'un exercice à
 * partir des ressentis récents, donnés du plus ancien au plus récent.
 *
 * Heuristique (sur les `window` dernières séances notées, défaut 3) :
 *  - aucune note exploitable                              -> MAINTIENS
 *  - dernière notée = dur                                 -> REDUIS   (priorité : on réagit
 *                                                            vite à une séance subitement trop dure)
 *  - dernière notée = facile sans dur dans la fenêtre     -> AUGMENTE
 *  - moyenne des scores <= 1.5 (proche de facile)         -> AUGMENTE
 *  - moyenne des scores >= 2.5 (proche de dur)            -> REDUIS
 *  - sinon (moyen dominant)                               -> MAINTIENS
 */
fun suggestProgression(recent: List<Difficulty?>, window: Int = 3): ProgressionAdvice {
    val scored = recent.mapNotNull(::difficultyScore)
    if (scored.isEmpty()) return ProgressionAdvice.MAINTIENS

    // Équivalent de `slice(-window)` : les `window` derniers ; `slice(-0)` en JS
    // renvoie tout le tableau, on garde ce comportement pour window <= 0.
    val lastN = if (window <= 0) scored else scored.takeLast(window)
    val last = lastN.last()
    val avg = lastN.sum().toDouble() / lastN.size

    if (last == 3) return ProgressionAdvice.REDUIS // dernière séance « dur » : on lève le pied
    if (last == 1 && 3 !in lastN) return ProgressionAdvice.AUGMENTE // facile, sans « dur » récent
    if (avg <= 1.5) return ProgressionAdvice.AUGMENTE
    if (avg >= 2.5) return ProgressionAdvice.REDUIS
    return ProgressionAdvice.MAINTIENS
}

/** Phrase d'action FR (tutoiement) à afficher pour un conseil donné. */
fun adviceLabel(a: ProgressionAdvice): String = when (a) {
    ProgressionAdvice.AUGMENTE -> "Augmente les reps ou la charge"
    ProgressionAdvice.REDUIS -> "Réduis la charge, soigne la technique"
    ProgressionAdvice.MAINTIENS -> "Maintiens, charge bien calibrée"
}
