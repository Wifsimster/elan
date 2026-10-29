package ovh.battistella.elan.domain

val PRIVACY_ZONE_OPTIONS: List<Int> = listOf(0, 100, 200, 500)

fun <T : GeoPoint> trimPrivacyZone(points: List<T>, radiusM: Double): List<T> {
    if (radiusM <= 0 || points.size < 2) return points
    val first = points[0]
    val last = points[points.size - 1]

    var start = 0
    while (start < points.size && haversineMeters(first, points[start]) < radiusM) start++
    var end = points.size - 1
    while (end > start && haversineMeters(last, points[end]) < radiusM) end--

    if (end - start < 1) return points
    return points.subList(start, end + 1)
}
