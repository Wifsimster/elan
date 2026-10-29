package ovh.battistella.elan.domain

fun canRetype(from: ActivityType, to: ActivityType): Boolean =
    from != to && isGpsActivity(from) && isGpsActivity(to)

data class RetypeChanges(
    val type: ActivityType,
    val calories: Double,
    val avgCadence: Double?,
    val maxCadence: Double?,
)

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

    val keepsCadence = to == ActivityType.VELO

    return RetypeChanges(
        type = to,
        calories = calories,
        avgCadence = if (keepsCadence) session.avgCadence else null,
        maxCadence = if (keepsCadence) session.maxCadence else null,
    )
}
