// (Dé)sérialisation en flux du fichier de sauvegarde (format 1 de l'app
// d'origine) : `{format, app, exportedAt, schema, data: DbSnapshot}`. Écrit et
// lu avec JsonWriter/JsonReader pour qu'une base de plusieurs dizaines de
// milliers de points GPS ne passe jamais en mémoire sous forme de texte JSON.
//
// Forme EXACTEMENT celle de `exportAll()` de `db.ts` (clés camelCase des
// colonnes, `null` explicites) : les sauvegardes restent échangeables entre
// les deux versions de l'app. Lecture tolérante : `bodyMeasurements` absent
// (schéma < 4), `hr`/`cadence`/`difficulty` absents, champs inconnus ignorés.
package ovh.battistella.elan.data.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import ovh.battistella.elan.data.repository.DbSnapshot
import ovh.battistella.elan.data.repository.SettingEntry
import ovh.battistella.elan.domain.BodyMeasurement
import ovh.battistella.elan.domain.Difficulty
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.toActivityType
import ovh.battistella.elan.domain.toJsString
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.Writer

/** Version du format de sauvegarde (indépendante du schéma SQLite). */
const val BACKUP_FORMAT = 1

/**
 * Version de schéma estampillée dans les sauvegardes : celle du schéma v7 de
 * l'app d'origine, que la base Room reprend tel quel. Une sauvegarde d'un
 * schéma plus récent est refusée à la restauration.
 */
const val BACKUP_SCHEMA = 7

/** Identifiant d'application dans l'enveloppe (inchangé depuis l'app d'origine). */
const val BACKUP_APP = "suivi-sport"

/** Enveloppe lue : chaque champ est `null` s'il manque (sauvegarde héritée ou fichier étranger). */
data class BackupEnvelope(
    val format: Int?,
    val app: String?,
    val exportedAt: Long?,
    val schema: Int?,
    val data: DbSnapshot?,
)

/** Fichier de sauvegarde syntaxiquement invalide (JSON illisible ou types incohérents). */
class BackupParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

object BackupSnapshotCodec {

    // ---- écriture --------------------------------------------------------

    /** Écrit l'enveloppe complète (format 1) dans `file`, en flux. */
    fun write(snapshot: DbSnapshot, exportedAt: Long, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { write(snapshot, exportedAt, it) }
    }

    fun write(snapshot: DbSnapshot, exportedAt: Long, out: OutputStream) {
        val w = JsonWriter(out.utf8Writer())
        w.beginObject()
        w.name("format").value(BACKUP_FORMAT.toLong())
        w.name("app").value(BACKUP_APP)
        w.name("exportedAt").value(exportedAt)
        w.name("schema").value(BACKUP_SCHEMA.toLong())
        w.name("data")
        writeSnapshot(w, snapshot)
        w.endObject()
        w.flush()
    }

    /** Écrit un `DbSnapshot` nu (sans enveloppe) — partagé avec l'export coach. */
    fun writeSnapshot(w: JsonWriter, snapshot: DbSnapshot) {
        w.beginObject()
        w.name("sessions").beginArray()
        for (s in snapshot.sessions) writeSession(w, s)
        w.endArray()
        w.name("trackPoints").beginArray()
        for (p in snapshot.trackPoints) writeTrackPoint(w, p)
        w.endArray()
        w.name("muscuSets").beginArray()
        for (m in snapshot.muscuSets) writeMuscuSet(w, m)
        w.endArray()
        w.name("bodyMeasurements").beginArray()
        for (b in snapshot.bodyMeasurements ?: emptyList()) {
            w.beginObject()
            w.name("id").value(b.id)
            w.name("measuredAt").value(b.measuredAt)
            w.name("weightKg").jsNumber(b.weightKg)
            w.endObject()
        }
        w.endArray()
        w.name("settings").beginArray()
        for (e in snapshot.settings) {
            w.beginObject()
            w.name("key").value(e.key)
            w.name("value").value(e.value)
            w.endObject()
        }
        w.endArray()
        w.endObject()
    }

    // Ordre des clés = ordre des colonnes de `SELECT * FROM sessions` de l'app d'origine.
    private fun writeSession(w: JsonWriter, s: Session) {
        w.beginObject()
        w.name("id").value(s.id)
        w.name("type").value(s.type.key)
        w.name("startedAt").value(s.startedAt)
        w.name("endedAt").nullableLong(s.endedAt)
        w.name("durationSec").value(s.durationSec.toLong())
        w.name("notes").value(s.notes)
        w.name("avgHr").jsNumber(s.avgHr)
        w.name("maxHr").jsNumber(s.maxHr)
        w.name("distanceM").jsNumber(s.distanceM)
        w.name("avgSpeedKmh").jsNumber(s.avgSpeedKmh)
        w.name("maxSpeedKmh").jsNumber(s.maxSpeedKmh)
        w.name("elevationGainM").jsNumber(s.elevationGainM)
        w.name("calories").jsNumber(s.calories)
        w.name("avgCadence").jsNumber(s.avgCadence)
        w.name("maxCadence").jsNumber(s.maxCadence)
        w.name("source").value(s.source)
        w.name("externalId").value(s.externalId)
        w.name("movingTimeSec").nullableLong(s.movingTimeSec?.toLong())
        w.endObject()
    }

    private fun writeTrackPoint(w: JsonWriter, p: TrackPoint) {
        w.beginObject()
        w.name("id").value(p.id)
        w.name("sessionId").value(p.sessionId)
        w.name("ts").value(p.ts)
        w.name("lat").jsNumber(p.lat)
        w.name("lon").jsNumber(p.lon)
        w.name("altitude").jsNumber(p.altitude)
        w.name("speedKmh").jsNumber(p.speedKmh)
        w.name("hr").jsNumber(p.hr)
        w.name("cadence").jsNumber(p.cadence)
        w.endObject()
    }

    private fun writeMuscuSet(w: JsonWriter, m: MuscuSet) {
        w.beginObject()
        w.name("id").value(m.id)
        w.name("sessionId").value(m.sessionId)
        w.name("exercise").value(m.exercise)
        w.name("setIndex").value(m.setIndex.toLong())
        w.name("reps").value(m.reps.toLong())
        w.name("weightKg").jsNumber(m.weightKg)
        w.name("difficulty").value(m.difficulty?.key)
        w.endObject()
    }

    private fun JsonWriter.nullableLong(v: Long?): JsonWriter = if (v == null) nullValue() else value(v)

    /**
     * Nombre au format JavaScript (`String(n)` : entier sans « .0 »).
     * `JsonWriter.value(Number)` écrit `toString()` tel quel, d'où ce
     * `Number` de façade ; l'app d'origine produisait exactement ces chaînes.
     */
    fun JsonWriter.jsNumber(v: Double?): JsonWriter =
        if (v == null || !v.isFinite()) nullValue() else value(JsNumberText(v))

    private class JsNumberText(private val v: Double) : Number() {
        private val text = v.toJsString()
        override fun toString(): String = text
        override fun toDouble(): Double = v
        override fun toFloat(): Float = v.toFloat()
        override fun toLong(): Long = v.toLong()
        override fun toInt(): Int = v.toInt()
        override fun toShort(): Short = v.toInt().toShort()
        override fun toByte(): Byte = v.toInt().toByte()
    }

    // ---- lecture ---------------------------------------------------------

    /** Lit l'enveloppe complète depuis `file`. [BackupParseException] si le JSON est illisible. */
    fun read(file: File): BackupEnvelope = file.inputStream().buffered().use { read(it.reader(Charsets.UTF_8)) }

    fun read(reader: java.io.Reader): BackupEnvelope {
        val r = JsonReader(reader)
        try {
            if (r.peek() != JsonToken.BEGIN_OBJECT) throw BackupParseException("Sauvegarde illisible (JSON invalide).")
            var format: Int? = null
            var app: String? = null
            var exportedAt: Long? = null
            var schema: Int? = null
            var data: DbSnapshot? = null
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "format" -> format = r.nextIntOrNull()
                    "app" -> app = r.nextStringOrNull()
                    "exportedAt" -> exportedAt = r.nextLongOrNull()
                    "schema" -> schema = r.nextIntOrNull()
                    "data" -> data = if (r.peek() == JsonToken.BEGIN_OBJECT) readSnapshot(r) else { r.skipValue(); null }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            return BackupEnvelope(format, app, exportedAt, schema, data)
        } catch (e: BackupParseException) {
            throw e
        } catch (e: Exception) {
            // IOException, IllegalStateException, NumberFormatException… tout
            // écart de syntaxe ou de type est « JSON invalide ».
            throw BackupParseException("Sauvegarde illisible (JSON invalide).", e)
        } finally {
            runCatching { r.close() }
        }
    }

    /** Lit un `DbSnapshot` nu (le lecteur est positionné sur son `{`). */
    fun readSnapshot(r: JsonReader): DbSnapshot {
        val sessions = ArrayList<Session>()
        val trackPoints = ArrayList<TrackPoint>()
        val muscuSets = ArrayList<MuscuSet>()
        var bodyMeasurements: ArrayList<BodyMeasurement>? = null
        val settings = ArrayList<SettingEntry>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "sessions" -> r.array { readSession(r)?.let(sessions::add) }
                "trackPoints" -> r.array { readTrackPoint(r)?.let(trackPoints::add) }
                "muscuSets" -> r.array { readMuscuSet(r)?.let(muscuSets::add) }
                "bodyMeasurements" -> {
                    if (r.peek() == JsonToken.BEGIN_ARRAY) {
                        val list = ArrayList<BodyMeasurement>()
                        r.array { readBodyMeasurement(r)?.let(list::add) }
                        bodyMeasurements = list
                    } else {
                        r.skipValue()
                    }
                }
                "settings" -> r.array { readSetting(r)?.let(settings::add) }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return DbSnapshot(sessions, trackPoints, muscuSets, bodyMeasurements, settings)
    }

    /** Un enregistrement JSON générique : on collecte les champs puis on valide les obligatoires. */
    private class Fields {
        val longs = HashMap<String, Long>()
        val doubles = HashMap<String, Double>()
        val strings = HashMap<String, String>()
        fun long(k: String): Long? = longs[k] ?: doubles[k]?.toLong()
        fun int(k: String): Int? = long(k)?.toInt()
        fun double(k: String): Double? = doubles[k] ?: longs[k]?.toDouble()
        fun string(k: String): String? = strings[k]
    }

    private fun JsonReader.readFields(): Fields {
        val f = Fields()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            when (peek()) {
                JsonToken.NUMBER -> {
                    val raw = nextString()
                    val asLong = raw.toLongOrNull()
                    if (asLong != null) f.longs[name] = asLong else f.doubles[name] = raw.toDouble()
                }
                JsonToken.STRING -> f.strings[name] = nextString()
                JsonToken.NULL -> nextNull()
                else -> skipValue()
            }
        }
        endObject()
        return f
    }

    private fun readSession(r: JsonReader): Session? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        val f = r.readFields()
        val id = f.long("id") ?: return null
        val startedAt = f.long("startedAt") ?: return null
        return Session(
            id = id,
            type = toActivityType(f.string("type")),
            startedAt = startedAt,
            endedAt = f.long("endedAt"),
            durationSec = f.int("durationSec") ?: 0,
            movingTimeSec = f.int("movingTimeSec"),
            notes = f.string("notes"),
            avgHr = f.double("avgHr"),
            maxHr = f.double("maxHr"),
            distanceM = f.double("distanceM"),
            avgSpeedKmh = f.double("avgSpeedKmh"),
            maxSpeedKmh = f.double("maxSpeedKmh"),
            elevationGainM = f.double("elevationGainM"),
            avgCadence = f.double("avgCadence"),
            maxCadence = f.double("maxCadence"),
            calories = f.double("calories"),
            source = f.string("source"),
            externalId = f.string("externalId"),
        )
    }

    private fun readTrackPoint(r: JsonReader): TrackPoint? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        val f = r.readFields()
        return TrackPoint(
            id = f.long("id") ?: return null,
            sessionId = f.long("sessionId") ?: return null,
            ts = f.long("ts") ?: return null,
            lat = f.double("lat") ?: return null,
            lon = f.double("lon") ?: return null,
            altitude = f.double("altitude"),
            speedKmh = f.double("speedKmh"),
            hr = f.double("hr"),
            cadence = f.double("cadence"),
        )
    }

    private fun readMuscuSet(r: JsonReader): MuscuSet? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        val f = r.readFields()
        return MuscuSet(
            id = f.long("id") ?: return null,
            sessionId = f.long("sessionId") ?: return null,
            exercise = f.string("exercise") ?: return null,
            setIndex = f.int("setIndex") ?: 0,
            reps = f.int("reps") ?: 0,
            weightKg = f.double("weightKg") ?: 0.0,
            difficulty = Difficulty.fromKey(f.string("difficulty")),
        )
    }

    private fun readBodyMeasurement(r: JsonReader): BodyMeasurement? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        val f = r.readFields()
        return BodyMeasurement(
            id = f.long("id") ?: return null,
            measuredAt = f.long("measuredAt") ?: return null,
            weightKg = f.double("weightKg") ?: return null,
        )
    }

    private fun readSetting(r: JsonReader): SettingEntry? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        val f = r.readFields()
        return SettingEntry(key = f.string("key") ?: return null, value = f.string("value") ?: return null)
    }

    private inline fun JsonReader.array(each: () -> Unit) {
        if (peek() != JsonToken.BEGIN_ARRAY) { skipValue(); return }
        beginArray()
        while (hasNext()) each()
        endArray()
    }

    private fun JsonReader.nextIntOrNull(): Int? = when (peek()) {
        JsonToken.NUMBER -> nextDouble().toInt()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    private fun JsonReader.nextLongOrNull(): Long? = when (peek()) {
        JsonToken.NUMBER -> { val raw = nextString(); raw.toLongOrNull() ?: raw.toDouble().toLong() }
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }

    private fun JsonReader.nextStringOrNull(): String? = when (peek()) {
        JsonToken.STRING -> nextString()
        JsonToken.NULL -> { nextNull(); null }
        else -> { skipValue(); null }
    }
}

/** `Writer` UTF-8 sur un flux, bufferisé (JsonWriter écrit caractère par caractère). */
private fun OutputStream.utf8Writer(): Writer = java.io.OutputStreamWriter(this, Charsets.UTF_8).buffered()
