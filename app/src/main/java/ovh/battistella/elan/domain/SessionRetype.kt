// Changement de type d'une séance déjà enregistrée (vélo ↔ course ↔ marche).
//
// Le type n'est pas qu'une étiquette : les calories ont été estimées avec la
// table MET de l'activité d'origine, et la cadence vient d'un capteur vélo. Ce
// module calcule — en pur, sans toucher la base — ce qui doit changer avec le
// type. L'écriture est dans la couche données (`changeSessionType`).
package ovh.battistella.elan.domain

/**
 * Le changement est-il permis ? Uniquement entre activités tracées au GPS :
 * passer de ou vers la musculation n'a pas de sens, et re-choisir le type
 * courant est un non-événement.
 */
fun canRetype(from: ActivityType, to: ActivityType): Boolean =
    from != to && isGpsActivity(from) && isGpsActivity(to)

/** Ce qu'un changement de type modifie sur la ligne de séance. */
data class RetypeChanges(
    val type: ActivityType,
    /** Calories ré-estimées avec la table MET de la nouvelle activité. */
    val calories: Double,
    /** Cadence : conservée à vélo, effacée dès qu'on passe à pied. */
    val avgCadence: Double?,
    val maxCadence: Double?,
)

/**
 * Nouvelles valeurs de la séance pour le type `to`, ou `null` si le changement
 * n'est pas permis. Les calories repartent des agrégats stockés avec la même
 * formule qu'à l'enregistrement : temps EN MOUVEMENT quand il est connu, et la
 * FC max du PROFIL (référence d'intensité), pas celle mesurée sur la séance.
 */
fun retypeChanges(session: Session, to: ActivityType, profile: Profile): RetypeChanges? {
    if (!canRetype(session.type, to)) return null

    val effectiveSec = session.movingTimeSec ?: session.durationSec
    val calories = estimateCalories(
        type = to,
        weightKg = profile.weightKg,
        durationSec = effectiveSec,
        avgSpeedKmh = session.avgSpeedKmh,
        elevationGainM = session.elevationGainM,
        avgHr = session.avgHr,
        maxHr = profile.maxHr,
    )

    // La cadence mesurée est celle d'un pédalier : elle ne veut plus rien dire à pied.
    val keepsCadence = to == ActivityType.VELO

    return RetypeChanges(
        type = to,
        calories = calories,
        avgCadence = if (keepsCadence) session.avgCadence else null,
        maxCadence = if (keepsCadence) session.maxCadence else null,
    )
}
