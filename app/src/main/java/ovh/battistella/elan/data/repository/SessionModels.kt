// Types d'entrée/sortie des dépôts de séances (équivalents des types exportés
// par `db.ts` : SessionUpdate, ListSessionsOptions, ImportedSessionRow…).
package ovh.battistella.elan.data.repository

import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.AggPoint
import ovh.battistella.elan.domain.Difficulty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Modification partielle d'une séance. Seules les propriétés AFFECTÉES sont
 * écrites (`UPDATE sessions SET a = ?, b = ?`), y compris quand on affecte
 * `null` (→ `SET a = NULL`) ; une propriété jamais touchée est laissée telle
 * quelle — c'est le `Partial<Pick<Session, …>>` d'origine, où seule la
 * présence de la clé compte. Un patch vide est un no-op.
 *
 * ```
 * SessionUpdate { endedAt = now; durationSec = 3600; notes = null }
 * ```
 */
class SessionUpdate {
    private val assigned = LinkedHashMap<String, Any?>()

    var endedAt: Long? by column("endedAt")
    var durationSec: Int? by column("durationSec")
    var movingTimeSec: Int? by column("movingTimeSec")
    var notes: String? by column("notes")
    var avgHr: Double? by column("avgHr")
    var maxHr: Double? by column("maxHr")
    var distanceM: Double? by column("distanceM")
    var avgSpeedKmh: Double? by column("avgSpeedKmh")
    var maxSpeedKmh: Double? by column("maxSpeedKmh")
    var elevationGainM: Double? by column("elevationGainM")
    var avgCadence: Double? by column("avgCadence")
    var maxCadence: Double? by column("maxCadence")
    var calories: Double? by column("calories")

    val isEmpty: Boolean get() = assigned.isEmpty()

    /** Colonnes affectées, dans l'ordre d'affectation (noms issus de la liste fermée ci-dessus). */
    val columns: List<String> get() = assigned.keys.toList()

    /** Valeurs correspondantes (`null` = SET NULL). */
    val values: List<Any?> get() = assigned.values.toList()

    private fun <T> column(name: String) = object : ReadWriteProperty<SessionUpdate, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: SessionUpdate, property: KProperty<*>): T? = assigned[name] as T?
        override fun setValue(thisRef: SessionUpdate, property: KProperty<*>, value: T?) {
            assigned[name] = value
        }
    }

    companion object {
        operator fun invoke(block: SessionUpdate.() -> Unit): SessionUpdate = SessionUpdate().apply(block)
    }
}

/** Options de filtrage de l'historique (`listSessions`). */
data class ListSessionsOptions(
    val limit: Int = 100,
    /** Décale la fenêtre (pagination). */
    val offset: Int = 0,
    /** Restreint à un type d'activité. */
    val type: ActivityType? = null,
    /** Recherche libre, insensible à la casse : code du type, notes, exercices muscu. */
    val search: String? = null,
    /** Borne basse de `startedAt` (ms epoch, incluse). */
    val fromMs: Long? = null,
    /** Borne haute de `startedAt` (ms epoch, exclue). */
    val toMs: Long? = null,
)

/** Un point GPS à écrire (l'id et la séance sont attribués à l'insertion). */
data class TrackPointInput(
    override val ts: Long,
    override val lat: Double,
    override val lon: Double,
    override val altitude: Double? = null,
    override val speedKmh: Double? = null,
    override val hr: Double? = null,
    override val cadence: Double? = null,
) : AggPoint

/** Une série muscu à écrire (`difficulty` facultatif : série sans ressenti noté). */
data class MuscuSetInput(
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val difficulty: Difficulty? = null,
)

/** Une séance prête à insérer depuis une source externe (fichier Strava). */
data class ImportedSession(
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

/** Résultat d'un import : inséré (avec son id) ou doublon ignoré (rien écrit). */
sealed interface ImportResult {
    data class Imported(val id: Long) : ImportResult
    data object Duplicate : ImportResult
}

/** Durée d'effort cumulée d'un jour local (`YYYY-MM-DD`), pour le graphe. */
data class DailyDuration(val day: String, val durationSec: Int)

/** Ligne d'index : un exercice connu et son dernier état. */
data class ExerciseSummary(
    val exercise: String,
    val sessions: Int,
    val lastWeightKg: Double,
    val lastAt: Long,
    /** Ressenti noté à la dernière séance (indice de progression dans l'index). */
    val lastDifficulty: Difficulty?,
)

/** Métrique sur laquelle une séance peut établir un record. */
enum class RecordKind { DISTANCE, ELEVATION, DURATION, SPEED }

/** Portée d'un record : sur l'année civile de la séance, ou sur tout l'historique. */
enum class RecordScope { YEAR, ALL }

data class SessionRecord(val kind: RecordKind, val scope: RecordScope)
