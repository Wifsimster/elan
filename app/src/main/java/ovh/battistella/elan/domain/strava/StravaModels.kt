package ovh.battistella.elan.domain.strava

data class ParsedPoint(
    val ts: Long?,
    val lat: Double?,
    val lon: Double?,
    val ele: Double?,
    val hr: Double?,
    val cad: Double?,
)

enum class ParsedSport { CYCLING, RUNNING, WALKING, OTHER, UNKNOWN }

data class ParsedActivity(
    val sport: ParsedSport,
    val startedAt: Long?,
    val points: List<ParsedPoint>,
    val distanceM: Double?,
    val calories: Double?,
)

enum class StravaFormat { GPX, TCX, FIT }

data class ParseResult(val format: StravaFormat, val activities: List<ParsedActivity>)

class StravaFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
