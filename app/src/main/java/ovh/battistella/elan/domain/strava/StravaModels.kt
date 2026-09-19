// Forme commune des activités décodées depuis un fichier Strava (GPX, TCX ou
// FIT) — port des types de `src/lib/strava/parse.ts`. La normalisation aval
// (`StravaImport`) est identique quel que soit le format d'origine.
package ovh.battistella.elan.domain.strava

/** Un point de tracé tel que lu dans le fichier : tout est facultatif. */
data class ParsedPoint(
    /** ms epoch (null si absent). */
    val ts: Long?,
    val lat: Double?,
    val lon: Double?,
    /** Altitude en mètres. */
    val ele: Double?,
    /** Fréquence cardiaque en bpm. */
    val hr: Double?,
    /** Cadence en tours/min. */
    val cad: Double?,
)

/**
 * Sport déclaré par le fichier. `OTHER` = sport identifié mais non supporté,
 * `UNKNOWN` = non déclaré (le GPX de Strava n'expose pas le sport).
 */
enum class ParsedSport { CYCLING, RUNNING, WALKING, OTHER, UNKNOWN }

data class ParsedActivity(
    val sport: ParsedSport,
    val startedAt: Long?,
    val points: List<ParsedPoint>,
    /** Distance fournie par le fichier (TCX/FIT), en mètres, si présente. */
    val distanceM: Double?,
    /** Calories fournies par le fichier (TCX/FIT), si présentes. */
    val calories: Double?,
)

enum class StravaFormat { GPX, TCX, FIT }

data class ParseResult(val format: StravaFormat, val activities: List<ParsedActivity>)

/** Fichier refusé (format inconnu, trop volumineux, XML dangereux, FIT corrompu). */
class StravaFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
