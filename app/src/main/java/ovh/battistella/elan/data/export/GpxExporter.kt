package ovh.battistella.elan.data.export

import android.content.Context
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.toJsString
import ovh.battistella.elan.domain.trimPrivacyZone
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
private const val TPX_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

fun escapeXml(s: String): String {
    val sb = StringBuilder(s.length + 16)
    for (c in s) {
        when (c) {
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '&' -> sb.append("&amp;")
            '\'' -> sb.append("&apos;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private val ISO_MS: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

private fun iso(ts: Long): String = ISO_MS.format(Instant.ofEpochMilli(ts))

private fun fixed(n: Double, digits: Int): String =
    BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toDouble().toJsString()

private fun coord(n: Double): String = fixed(n, 7)

private class GpxKind(val type: String, val title: String)

private fun gpxKind(type: ActivityType): GpxKind = when (type) {
    ActivityType.COURSE -> GpxKind("running", "Course à pied")
    ActivityType.MARCHE -> GpxKind("walking", "Marche")
    else -> GpxKind("cycling", "Sortie vélo")
}

fun rideName(session: Session): String {
    val h = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).hour
    val moment = when {
        h < 6 -> "Nuit"
        h < 12 -> "Matin"
        h < 18 -> "Après-midi"
        else -> "Soir"
    }
    return "${gpxKind(session.type).title} — $moment"
}

fun rideFileName(session: Session): String {
    val d = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault())
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "elan-${session.type.key}-${d.year}-${p(d.monthValue)}-${p(d.dayOfMonth)}-${p(d.hour)}${p(d.minute)}.gpx"
}

fun buildRideGpx(session: Session, points: List<TrackPoint>): String {
    val lines = ArrayList<String>(points.size * 4 + 16)
    lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    lines.add("<gpx version=\"1.1\" creator=\"Élan\" xmlns=\"$GPX_NS\" xmlns:gpxtpx=\"$TPX_NS\">")
    lines.add("  <metadata>")
    lines.add("    <time>${iso(session.startedAt)}</time>")
    lines.add("  </metadata>")
    lines.add("  <trk>")
    lines.add("    <name>${escapeXml(rideName(session))}</name>")
    lines.add("    <type>${gpxKind(session.type).type}</type>")
    lines.add("    <trkseg>")

    for (p in points) {
        lines.add("      <trkpt lat=\"${coord(p.lat)}\" lon=\"${coord(p.lon)}\">")
        if (p.altitude != null) lines.add("        <ele>${fixed(p.altitude, 1)}</ele>")
        lines.add("        <time>${iso(p.ts)}</time>")

        val hr = p.hr?.takeIf { it > 0 }?.let { Math.round(it) }
        val cad = p.cadence?.takeIf { it > 0 }?.let { Math.round(it) }
        if (hr != null || cad != null) {
            lines.add("        <extensions>")
            lines.add("          <gpxtpx:TrackPointExtension>")
            if (hr != null) lines.add("            <gpxtpx:hr>$hr</gpxtpx:hr>")
            if (cad != null) lines.add("            <gpxtpx:cad>$cad</gpxtpx:cad>")
            lines.add("          </gpxtpx:TrackPointExtension>")
            lines.add("        </extensions>")
        }
        lines.add("      </trkpt>")
    }

    lines.add("    </trkseg>")
    lines.add("  </trk>")
    lines.add("</gpx>")
    return lines.joinToString("\n")
}

const val GPX_MIME = "application/gpx+xml"

@Singleton
class GpxExporter @Inject constructor(
    private val sessions: SessionRepository,
) {
    suspend fun exportSession(context: Context, sessionId: Long, privacyZoneM: Double): File? {
        val session = sessions.getSession(sessionId) ?: return null
        val all = sessions.getTrackPoints(sessionId)
        if (all.size < 2) return null
        val points = trimPrivacyZone(all, privacyZoneM)
        val file = File(FileShare.shareDir(context), rideFileName(session))
        file.writeText(buildRideGpx(session, points), Charsets.UTF_8)
        return file
    }
}
