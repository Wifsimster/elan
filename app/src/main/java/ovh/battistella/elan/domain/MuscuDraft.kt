package ovh.battistella.elan.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val MUSCU_DRAFT_SETTING_KEY = "muscu_draft"

data class MuscuDraft(
    val version: Int = 1,
    val startedAt: Long,
    val elapsedSec: Double,
    val exercises: JSONArray,
    val hrSamples: List<HrSample>,
)

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
        null
    }
}

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
