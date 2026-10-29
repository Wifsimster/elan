package ovh.battistella.elan.domain.strava

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

object GpxTcxParser {

    const val MAX_CHARS = 30_000_000

    private val GPX_ROOT = Regex("<gpx[\\s>]", RegexOption.IGNORE_CASE)
    private val TCX_ROOT = Regex("<TrainingCenterDatabase[\\s>]", RegexOption.IGNORE_CASE)
    private val UNSAFE_XML = Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE)

    fun parse(content: String): ParseResult {
        if (content.length > MAX_CHARS) throw StravaFileException("Fichier trop volumineux.")
        if (UNSAFE_XML.containsMatchIn(content)) {
            throw StravaFileException("Fichier refusé : déclaration DOCTYPE/ENTITY non autorisée.")
        }
        return when {
            GPX_ROOT.containsMatchIn(content) -> {
                val h = GpxHandler()
                run(content, h)
                ParseResult(StravaFormat.GPX, listOf(h.activity()))
            }
            TCX_ROOT.containsMatchIn(content) -> {
                val h = TcxHandler()
                run(content, h)
                ParseResult(StravaFormat.TCX, h.activities)
            }
            else -> throw StravaFileException("Format non reconnu (ni GPX ni TCX).")
        }
    }

    private fun run(content: String, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        for (feature in SECURITY_FEATURES) runCatching { factory.setFeature(feature.first, feature.second) }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        val xml = content.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        try {
            reader.parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw StravaFileException("Fichier XML illisible.", e)
        }
    }

    private val SECURITY_FEATURES = listOf(
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    private val JS_NUMBER = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$")

    fun num(s: String?): Double? {
        val t = s?.trim() ?: return null
        if (t.isEmpty() || !JS_NUMBER.matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    private val OFFSET_NO_COLON: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss[.SSSSSSSSS][.SSSSSS][.SSS][.SS][.S]XX")

    fun parseTime(s: String?): Long? {
        val t = s?.trim() ?: return null
        if (t.isEmpty()) return null
        try {
            return OffsetDateTime.parse(t).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDateTime.parse(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        return try {
            OffsetDateTime.parse(t, OFFSET_NO_COLON).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun local(qName: String): String = qName.substringAfterLast(':')

    private fun attr(attributes: Attributes, name: String): String? {
        for (i in 0 until attributes.length) {
            if (local(attributes.getQName(i)).equals(name, ignoreCase = true)) return attributes.getValue(i)
        }
        return null
    }

    private class PointBuilder(val lat: Double?, val lon: Double?) {
        var ts: Long? = null
        var ele: Double? = null
        var hr: Double? = null
        var cad: Double? = null
        val seen = HashSet<String>()
        inline fun once(tag: String, assign: () -> Unit) {
            if (seen.add(tag)) assign()
        }
        fun build() = ParsedPoint(ts, lat, lon, ele, hr, cad)
    }

    private class GpxHandler : DefaultHandler() {
        private val points = ArrayList<ParsedPoint>()
        private var startedAt: Long? = null
        private var startedAtSeen = false
        private var sport = ParsedSport.UNKNOWN
        private var sportSeen = false
        private var point: PointBuilder? = null
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            text.setLength(0)
            if (local(qName).equals("trkpt", ignoreCase = true) && point == null) {
                point = PointBuilder(lat = num(attr(attributes, "lat")), lon = num(attr(attributes, "lon")))
            }
        }

        private fun sportOf(type: String): ParsedSport = when (type.trim().lowercase()) {
            "cycling", "biking" -> ParsedSport.CYCLING
            "running" -> ParsedSport.RUNNING
            "walking", "hiking" -> ParsedSport.WALKING
            else -> ParsedSport.UNKNOWN
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val tag = local(qName)
            val value = text.toString()
            if (!startedAtSeen && tag.equals("time", ignoreCase = true)) {
                startedAtSeen = true
                startedAt = parseTime(value)
            }
            val p = point
            if (p != null) {
                when (tag.lowercase()) {
                    "trkpt" -> { points.add(p.build()); point = null }
                    "time" -> p.once("time") { p.ts = parseTime(value) }
                    "ele" -> p.once("ele") { p.ele = num(value) }
                    "hr" -> p.once("hr") { p.hr = num(value) }
                    "cad" -> p.once("cad") { p.cad = num(value) }
                }
            } else if (!sportSeen && tag.equals("type", ignoreCase = true)) {
                sportSeen = true
                sport = sportOf(value)
            }
            text.setLength(0)
        }

        fun activity() = ParsedActivity(sport, startedAt, points, distanceM = null, calories = null)
    }

    private class TcxHandler : DefaultHandler() {
        val activities = ArrayList<ParsedActivity>()
        private val stack = ArrayList<String>()
        private val text = StringBuilder()

        private var inActivity = false
        private var sport = ParsedSport.UNKNOWN
        private var points = ArrayList<ParsedPoint>()
        private var idTs: Long? = null
        private var idSeen = false
        private var distanceM: Double? = null
        private var calories: Double? = null

        private var lapDistanceSeen = false
        private var lapCaloriesSeen = false

        private var point: PointBuilder? = null
        private var lat: Double? = null
        private var lon: Double? = null
        private var positionSeen = false

        private fun parent(): String? = stack.getOrNull(stack.size - 2)
        private fun inside(tag: String): Boolean = stack.any { it.equals(tag, ignoreCase = true) }

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            text.setLength(0)
            val tag = local(qName)
            stack.add(tag)
            when (tag.lowercase()) {
                "activity" -> if (!inActivity) {
                    inActivity = true
                    sport = sportOf(attr(attributes, "Sport") ?: "")
                    points = ArrayList()
                    idTs = null
                    idSeen = false
                    distanceM = null
                    calories = null
                }
                "lap" -> { lapDistanceSeen = false; lapCaloriesSeen = false }
                "trackpoint" -> if (point == null) {
                    point = PointBuilder(null, null)
                    lat = null
                    lon = null
                    positionSeen = false
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val tag = local(qName)
            val value = text.toString()
            val parent = parent()
            val p = point
            if (p != null) {
                when (tag.lowercase()) {
                    "trackpoint" -> {
                        points.add(ParsedPoint(p.ts, lat, lon, p.ele, p.hr, p.cad))
                        point = null
                    }
                    "time" -> p.once("time") { p.ts = parseTime(value) }
                    "position" -> positionSeen = true
                    "latitudedegrees" -> if (!positionSeen && parent.equals("Position", ignoreCase = true)) {
                        p.once("lat") { lat = num(value) }
                    }
                    "longitudedegrees" -> if (!positionSeen && parent.equals("Position", ignoreCase = true)) {
                        p.once("lon") { lon = num(value) }
                    }
                    "altitudemeters" -> p.once("ele") { p.ele = num(value) }
                    "value" -> if (parent.equals("HeartRateBpm", ignoreCase = true)) p.once("hr") { p.hr = num(value) }
                    "cadence" -> p.once("cad") { p.cad = num(value) }
                }
            } else if (inActivity) {
                when (tag.lowercase()) {
                    "activity" -> {
                        activities.add(
                            ParsedActivity(
                                sport = sport,
                                startedAt = idTs ?: points.firstOrNull()?.ts,
                                points = points,
                                distanceM = distanceM,
                                calories = calories,
                            ),
                        )
                        inActivity = false
                    }
                    "id" -> if (!idSeen && parent.equals("Activity", ignoreCase = true)) {
                        idSeen = true
                        idTs = parseTime(value)
                    }
                    "distancemeters" -> if (!lapDistanceSeen && parent.equals("Lap", ignoreCase = true)) {
                        lapDistanceSeen = true
                        num(value)?.let { distanceM = (distanceM ?: 0.0) + it }
                    }
                    "calories" -> if (!lapCaloriesSeen && parent.equals("Lap", ignoreCase = true)) {
                        lapCaloriesSeen = true
                        num(value)?.let { calories = (calories ?: 0.0) + it }
                    }
                }
            }
            stack.removeAt(stack.size - 1)
            text.setLength(0)
        }

        private fun sportOf(attr: String): ParsedSport = when {
            Regex("bik|cycl", RegexOption.IGNORE_CASE).containsMatchIn(attr) -> ParsedSport.CYCLING
            Regex("run|cours", RegexOption.IGNORE_CASE).containsMatchIn(attr) -> ParsedSport.RUNNING
            Regex("walk|hik|march", RegexOption.IGNORE_CASE).containsMatchIn(attr) -> ParsedSport.WALKING
            attr.isNotEmpty() -> ParsedSport.OTHER
            else -> ParsedSport.UNKNOWN
        }
    }
}
