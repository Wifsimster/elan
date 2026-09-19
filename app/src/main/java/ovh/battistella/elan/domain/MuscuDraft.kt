// Brouillon de séance muscu « en pause » : permet de quitter une séance sans la
// terminer, puis de la reprendre plus tard avec tout son état (exercices,
// séries cochées, chrono, échantillons FC). 100 % local : un blob JSON dans la
// table `settings` (clé `muscu_draft`), donc aucune migration de schéma et rien
// sur le réseau. Ce fichier porte le modèle et sa (dé)sérialisation validée ;
// l'accès à la table est laissé à la couche données.
package ovh.battistella.elan.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Clé de réglage sous laquelle la couche données persiste le brouillon. */
const val MUSCU_DRAFT_SETTING_KEY = "muscu_draft"

/**
 * Snapshot sérialisable d'une séance muscu en cours. Les exercices sont
 * conservés tels quels (JSON opaque) : leur forme appartient à l'écran muscu.
 */
data class MuscuDraft(
    val version: Int = 1,
    /** Horodatage de début de séance (ms epoch), conservé tel quel à la reprise. */
    val startedAt: Long,
    /** Temps actif écoulé en secondes au moment de la sauvegarde. */
    val elapsedSec: Double,
    /** Exercices de la séance, forme libre (objets JSON de l'écran muscu). */
    val exercises: JSONArray,
    val hrSamples: List<HrSample>,
)

/** Sérialise un brouillon en JSON (`{version:1, startedAt, elapsedSec, exercises, hrSamples}`). */
fun serializeMuscuDraft(draft: MuscuDraft): String {
    val samples = JSONArray()
    for (s in draft.hrSamples) samples.put(JSONObject().put("ts", s.ts).put("hr", s.hr))
    return JSONObject()
        .put("version", draft.version)
        .put("startedAt", draft.startedAt)
        .put("elapsedSec", draft.elapsedSec)
        .put("exercises", draft.exercises)
        .put("hrSamples", samples)
        .toString()
}

/**
 * Relit un brouillon v1 depuis sa valeur brute. `null` si absent, corrompu ou mal
 * formé (`version !== 1`, `startedAt`/`elapsedSec` non numériques, `exercises`
 * non tableau) : on repart alors sur une séance vierge. `hrSamples` non tableau → [].
 */
fun parseMuscuDraft(raw: String?): MuscuDraft? {
    if (raw.isNullOrEmpty()) return null
    return try {
        val parsed = JSONObject(raw)
        val version = parsed.opt("version")
        val startedAt = parsed.opt("startedAt")
        val elapsedSec = parsed.opt("elapsedSec")
        val exercises = parsed.opt("exercises")
        if (
            version is Number && version.toDouble() == 1.0 &&
            startedAt is Number &&
            elapsedSec is Number &&
            exercises is JSONArray
        ) {
            MuscuDraft(
                version = 1,
                startedAt = startedAt.toLong(),
                elapsedSec = elapsedSec.toDouble(),
                exercises = exercises,
                hrSamples = hrSamplesFrom(parsed.opt("hrSamples")),
            )
        } else {
            null
        }
    } catch (e: JSONException) {
        // brouillon corrompu : on l'ignore (repart sur une séance vierge)
        null
    }
}

/** Échantillons FC d'un tableau JSON (`[{ts, hr}]`), `[]` si ce n'est pas un tableau. */
private fun hrSamplesFrom(value: Any?): List<HrSample> {
    if (value !is JSONArray) return emptyList()
    val out = ArrayList<HrSample>(value.length())
    for (i in 0 until value.length()) {
        val o = value.optJSONObject(i) ?: continue
        val ts = o.opt("ts")
        val hr = o.opt("hr")
        if (ts is Number && hr is Number) out.add(HrSample(ts.toLong(), hr.toDouble()))
    }
    return out
}
