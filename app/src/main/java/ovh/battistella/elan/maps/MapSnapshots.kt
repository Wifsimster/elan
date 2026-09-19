// Capture d'un fond de carte statique pour la carte de partage (port de
// lib/static-map.ts). MapLibre rend une image hors écran via `MapSnapshotter`,
// cadrée sur l'emprise du tracé. Aucune tuile n'est demandée à un tiers : le
// style pointe uniquement vers le serveur choisi par l'utilisateur, et en cas
// d'échec (hors-ligne, pas de style, délai dépassé) on retourne `null` —
// l'appelant retombe sur le tracé sans fond.
package ovh.battistella.elan.maps

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import ovh.battistella.elan.domain.GeoPoint
import kotlin.coroutines.resume

object MapSnapshots {
    private const val TAG = "MapSnapshots"
    private const val TIMEOUT_MS = 10_000L

    /**
     * Fond de carte cadré sur [points] (marge [paddingPx]), ou `null` si moins
     * de 2 points, pas de style valide, échec du rendu ou délai de 10 s dépassé.
     */
    suspend fun snapshotRoute(
        context: Context,
        points: List<GeoPoint>,
        styleUrl: String,
        width: Int = 360,
        height: Int = 200,
        paddingPx: Int = 24,
    ): Bitmap? {
        if (points.size < 2) return null
        if (styleUrl.isBlank() || !isValidMapStyleUrl(styleUrl)) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                // Le snapshotter veut le thread principal (looper) pour ses rappels.
                withContext(Dispatchers.Main.immediate) {
                    ensureMapLibre(context)
                    val bounds = paddedBounds(points, width, height, paddingPx)
                    val options = MapSnapshotter.Options(width, height)
                        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
                        .withRegion(bounds)
                        .withLogo(false)
                    val snapshotter = MapSnapshotter(context.applicationContext, options)
                    try {
                        suspendCancellableCoroutine<Bitmap?> { cont ->
                            cont.invokeOnCancellation { snapshotter.cancel() }
                            snapshotter.start(
                                { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                                { error ->
                                    Log.w(TAG, "capture impossible : $error")
                                    if (cont.isActive) cont.resume(null)
                                },
                            )
                        }
                    } finally {
                        snapshotter.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture ignorée", e)
            null
        }
    }

    /**
     * Emprise du tracé élargie d'une marge de [paddingPx] pixels de chaque
     * côté (le snapshotter n'a pas de padding : on agrandit la région en
     * proportion de l'image), avec un minimum pour un tracé quasi ponctuel.
     */
    internal fun paddedBounds(points: List<GeoPoint>, width: Int, height: Int, paddingPx: Int): LatLngBounds {
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }
        val latSpan = (maxLat - minLat).coerceAtLeast(MIN_SPAN_DEG)
        val lonSpan = (maxLon - minLon).coerceAtLeast(MIN_SPAN_DEG)
        val padLat = latSpan * paddingPx / (height - 2 * paddingPx).coerceAtLeast(1)
        val padLon = lonSpan * paddingPx / (width - 2 * paddingPx).coerceAtLeast(1)
        return LatLngBounds.from(
            (maxLat + padLat).coerceAtMost(85.0),
            maxLon + padLon,
            (minLat - padLat).coerceAtLeast(-85.0),
            minLon - padLon,
        )
    }

    /** ≈ 100 m : un tracé sans étendue reste lisible. */
    private const val MIN_SPAN_DEG = 0.001
}
