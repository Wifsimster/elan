// Tests du brouillon de séance muscu (MuscuDraft.kt) : relecture validée du
// JSON v1, rejet des valeurs corrompues, repli des échantillons FC. Robolectric
// uniquement pour `org.json`.
package ovh.battistella.elan.domain

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MuscuDraftTest {
    private val valid = """{
        "version": 1,
        "startedAt": 1750000000000,
        "elapsedSec": 125,
        "exercises": [{"name": "Goblet squat", "sets": [{"reps": 10, "weightKg": 20, "done": true}]}],
        "hrSamples": [{"ts": 1750000001000, "hr": 120}, {"ts": 1750000002000, "hr": 125}]
    }"""

    @Test
    fun `brouillon v1 valide - relu avec tous ses champs`() {
        val d = parseMuscuDraft(valid)
        assertNotNull(d)
        assertEquals(1, d!!.version)
        assertEquals(1750000000000L, d.startedAt)
        assertEquals(125.0, d.elapsedSec, 0.0)
        assertEquals(1, d.exercises.length())
        assertEquals("Goblet squat", d.exercises.getJSONObject(0).getString("name"))
        assertEquals(listOf(HrSample(1750000001000L, 120.0), HrSample(1750000002000L, 125.0)), d.hrSamples)
    }

    @Test
    fun `JSON corrompu ou absent - null`() {
        assertNull(parseMuscuDraft(null))
        assertNull(parseMuscuDraft(""))
        assertNull(parseMuscuDraft("{not json"))
        assertNull(parseMuscuDraft("[]"))
    }

    @Test
    fun `version ou champs mal formés - null`() {
        assertNull(parseMuscuDraft("""{"version":2,"startedAt":1,"elapsedSec":1,"exercises":[]}"""))
        assertNull(parseMuscuDraft("""{"startedAt":1,"elapsedSec":1,"exercises":[]}"""))
        assertNull(parseMuscuDraft("""{"version":1,"startedAt":"1","elapsedSec":1,"exercises":[]}"""))
        assertNull(parseMuscuDraft("""{"version":1,"startedAt":1,"elapsedSec":null,"exercises":[]}"""))
        assertNull(parseMuscuDraft("""{"version":1,"startedAt":1,"elapsedSec":1,"exercises":{}}"""))
        assertNull(parseMuscuDraft("""{"version":1,"startedAt":1,"elapsedSec":1}"""))
    }

    @Test
    fun `hrSamples non tableau - liste vide`() {
        val d = parseMuscuDraft("""{"version":1,"startedAt":1,"elapsedSec":1,"exercises":[],"hrSamples":"x"}""")
        assertEquals(emptyList<HrSample>(), d!!.hrSamples)
        val absent = parseMuscuDraft("""{"version":1,"startedAt":1,"elapsedSec":1,"exercises":[]}""")
        assertEquals(emptyList<HrSample>(), absent!!.hrSamples)
    }

    @Test
    fun `round-trip serialize vers parse`() {
        val draft = MuscuDraft(
            startedAt = 1750000000000L,
            elapsedSec = 42.5,
            exercises = JSONArray("""[{"name":"Pompes","sets":[]}]"""),
            hrSamples = listOf(HrSample(1750000001000L, 130.0)),
        )
        val back = parseMuscuDraft(serializeMuscuDraft(draft))
        assertNotNull(back)
        assertEquals(draft.startedAt, back!!.startedAt)
        assertEquals(draft.elapsedSec, back.elapsedSec, 0.0)
        assertEquals(draft.hrSamples, back.hrSamples)
        assertEquals("Pompes", back.exercises.getJSONObject(0).getString("name"))
    }
}
