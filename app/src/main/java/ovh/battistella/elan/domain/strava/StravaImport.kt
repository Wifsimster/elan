// Normalisation des activités Strava parsées vers le modèle du domaine (port de
// `src/lib/strava/import.ts`). Pur (aucun accès base/IO) : décode → normalise
// → clé de déduplication. L'insertion est faite par `insertImportedSession`
// et l'orchestration IO par `StravaImporter` (couche données).
package ovh.battistella.elan.domain.strava

import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.AggPoint
import ovh.battistella.elan.domain.LatLon
import ovh.battistella.elan.domain.estimateCalories
import ovh.battistella.elan.domain.haversineMeters
import ovh.battistella.elan.domain.movingTimeSec
import ovh.battistella.elan.domain.toJsString
import java.security.MessageDigest
import kotlin.math.max

/** Un point GPS importé (l'id et la séance sont attribués à l'insertion). */
data class ImportedPoint(
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double?,
    override val speedKmh: Double?,
    override val hr: Double?,
    override val cadence: Double?,
) : AggPoint

/** Séance normalisée, prête à être insérée (équivalent de `ImportedSession` d'origine). */
data class ImportedSessionDraft(
    val type: ActivityType,
    val startedAt: Long,
    val endedAt: Long,
    val durationSec: Int,
    val movingTimeSec: Int?,
    val notes: String?,
    val avgHr: Double?,
    val maxHr: Double?,
    val distanceM: Double?,
    val avgSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val elevationGainM: Double?,
    val avgCadence: Double?,
    val maxCadence: Double?,
    val calories: Double?,
    val source: String,
    val externalId: String,
)

data class ImportedDraft(val session: ImportedSessionDraft, val points: List<ImportedPoint>)

/** Séances importables et motifs (FR) des activités ignorées. */
data class ImportBuild(val drafts: List<ImportedDraft>, val skipped: List<String>)

object StravaImport {

    /** Garde-fou contre les pics GPS (un saut de point produit une vitesse absurde). */
    const val MAX_PLAUSIBLE_SPEED_KMH = 160.0

    /** Seuil anti-bruit pour le dénivelé positif (m). */
    const val ELEVATION_NOISE_M = 0.5

    const val SOURCE = "strava"
    const val NOTES = "Importé depuis Strava"

    /** Motif (FR) d'une activité ignorée pour son sport, affiché dans le bilan d'import. */
    const val SKIPPED_UNSUPPORTED_TYPE = "ignorée, type d'activité non pris en charge"

    /** Décode un fichier (octets : GPX/TCX/FIT, éventuellement gzip) et construit les séances importables. */
    fun buildDrafts(bytes: ByteArray, weightKg: Double): ImportBuild {
        val parsed = StravaDecoder.decode(bytes)
        val drafts = ArrayList<ImportedDraft>()
        val skipped = ArrayList<String>()
        for (act in parsed.activities) {
            when (val r = normalize(act, weightKg)) {
                is Normalized.Draft -> drafts.add(r.draft)
                is Normalized.Skipped -> skipped.add(r.reason)
            }
        }
        return ImportBuild(drafts, skipped)
    }

    private sealed interface Normalized {
        class Draft(val draft: ImportedDraft) : Normalized
        class Skipped(val reason: String) : Normalized
    }

    fun isValidLatLon(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat == 0.0 && lon == 0.0)

    /**
     * Moyenne (arrondie) et max en excluant les valeurs nulles ET les zéros de
     * dropout capteur (FC/cadence 0 lors d'une perte de signal) — sinon ils
     * tirent la moyenne vers le bas. Cohérent avec le tracker live.
     */
    fun avgMax(values: List<Double?>): Pair<Double?, Double?> {
        var sum = 0.0
        var count = 0
        var max: Double? = null
        for (v in values) {
            if (v == null || v <= 0) continue
            sum += v
            count++
            if (max == null || v > max) max = v
        }
        return if (count == 0) null to null else Math.round(sum / count).toDouble() to max
    }

    /** Dénivelé positif arrondi (crédit des montées > 0,5 m), `null` sans aucune altitude. */
    fun computeGain(points: List<ParsedPoint>): Double? {
        var gain = 0.0
        var prevEle: Double? = null
        var any = false
        for (p in points) {
            val ele = p.ele ?: continue
            any = true
            if (prevEle != null) {
                val d = ele - prevEle
                if (d > ELEVATION_NOISE_M) gain += d
            }
            prevEle = ele
        }
        return if (any) Math.round(gain).toDouble() else null
    }

    /** Normalise une activité ; renvoie un motif si elle doit être ignorée. */
    private fun normalize(act: ParsedActivity, weightKg: Double): Normalized {
        // Sport identifié mais non couvert par l'app : on ignore plutôt que de
        // l'importer sous une mauvaise activité. Comme dans l'app d'origine,
        // le FIT ne reconnaît que le vélo (course / marche FIT sont donc
        // ignorées) et le TCX vélo / course / marche ; un sport non déclaré
        // (GPX Strava, FIT « generic ») reste importé en vélo.
        if (act.sport == ParsedSport.OTHER) return Normalized.Skipped(SKIPPED_UNSUPPORTED_TYPE)
        val type = when (act.sport) {
            ParsedSport.RUNNING -> ActivityType.COURSE
            ParsedSport.WALKING -> ActivityType.MARCHE
            else -> ActivityType.VELO
        }

        val timed = act.points.filter { it.ts != null }
        if (timed.isEmpty()) return Normalized.Skipped("aucun horodatage exploitable")

        var startedAt = timed[0].ts!!
        var endedAt = startedAt
        for (p in timed) {
            val t = p.ts!!
            if (t < startedAt) startedAt = t
            if (t > endedAt) endedAt = t
        }
        val durationSec = max(0L, Math.round((endedAt - startedAt) / 1000.0)).toInt()
        // Activité dégénérée (un seul horodatage / durée nulle) : on l'ignore
        // plutôt que de persister une séance fantôme à 0 métrique. On garde les
        // imports sans GPS (home-trainer TCX avec FC/cadence).
        if (durationSec <= 0) return Normalized.Skipped("séance trop courte ou incomplète")

        // Points GPS valides → seuls ceux-là sont insérés (lat/lon NOT NULL en base).
        val gps = timed.filter { isValidLatLon(it.lat, it.lon) }

        var distanceM = act.distanceM
        var maxSpeedKmh: Double? = null
        val outPoints = ArrayList<ImportedPoint>(gps.size)
        var prev: ParsedPoint? = null
        var accDist = 0.0

        for (p in gps) {
            var speedKmh: Double? = null
            if (prev != null) {
                val dtSec = (p.ts!! - prev.ts!!) / 1000.0
                val dM = haversineMeters(LatLon(prev.lat!!, prev.lon!!), LatLon(p.lat!!, p.lon!!))
                if (dtSec > 0) {
                    val s = dM / dtSec * 3.6
                    if (s <= MAX_PLAUSIBLE_SPEED_KMH) {
                        // Segment plausible : on crédite sa distance et sa vitesse.
                        // Un saut (téléportation GPS) est ÉCARTÉ de la distance
                        // aussi — plafonner la seule vitesse laissait la distance
                        // (et donc l'externalId) gonflée.
                        accDist += dM
                        speedKmh = s
                        if (maxSpeedKmh == null || s > maxSpeedKmh) maxSpeedKmh = s
                    }
                }
            }
            outPoints.add(
                ImportedPoint(
                    ts = p.ts!!,
                    lat = p.lat!!,
                    lon = p.lon!!,
                    altitude = p.ele,
                    speedKmh = speedKmh,
                    hr = p.hr,
                    cadence = p.cad,
                ),
            )
            prev = p
        }
        if (distanceM == null && gps.size >= 2) distanceM = accDist

        // Temps en mouvement (hors arrêts) déduit du tracé : base de la vitesse
        // moyenne et des calories, plus juste que le temps total quand la
        // sortie comporte de longues pauses. À défaut de tracé, on retombe sur
        // la durée.
        val moving = movingTimeSec(outPoints)
        val movingTime = if (moving > 0) moving else null
        val effectiveSec = if (moving > 0) moving else durationSec
        val avgSpeedKmh = if (distanceM != null && effectiveSec > 0) distanceM / effectiveSec * 3.6 else null
        // Dénivelé calculé sur `timed` (superset dont `gps` est filtré) :
        // récupéré aussi pour les fichiers altitude-seule / à trous GPS.
        val elevationGainM = computeGain(timed)
        val (avgHr, maxHr) = avgMax(timed.map { it.hr })
        val (avgCadence, maxCadence) = avgMax(timed.map { it.cad })

        val calories = act.calories
            ?: if (effectiveSec > 0) estimateCalories(type, weightKg, effectiveSec, avgSpeedKmh) else null

        val first = gps.firstOrNull()
        val externalId = "strava-" + sha256Hex(
            listOf(
                startedAt.toDouble().toJsString(),
                durationSec.toDouble().toJsString(),
                Math.round(distanceM ?: 0.0).toDouble().toJsString(),
                first?.lat?.toJsString() ?: "na",
                first?.lon?.toJsString() ?: "na",
            ).joinToString("|"),
        ).take(24)

        return Normalized.Draft(
            ImportedDraft(
                session = ImportedSessionDraft(
                    type = type,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationSec = durationSec,
                    movingTimeSec = movingTime,
                    notes = NOTES,
                    avgHr = avgHr,
                    maxHr = maxHr,
                    distanceM = distanceM,
                    avgSpeedKmh = avgSpeedKmh,
                    maxSpeedKmh = maxSpeedKmh,
                    elevationGainM = elevationGainM,
                    avgCadence = avgCadence,
                    maxCadence = maxCadence,
                    calories = calories,
                    source = SOURCE,
                    externalId = externalId,
                ),
                points = outPoints,
            ),
        )
    }

    private fun sha256Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v shr 4]).append(HEX[v and 0xf])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
