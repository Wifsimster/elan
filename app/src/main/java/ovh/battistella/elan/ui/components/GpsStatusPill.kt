package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import kotlin.math.roundToInt

/** Libellé du point d'état GPS (pur, pour les tests). */
internal fun gpsStatusLabel(status: GpsStatus, accuracyM: Double?): String = when {
    status == GpsStatus.DENIED -> "GPS refusé"
    status == GpsStatus.TRACKING && accuracyM != null && accuracyM <= 10 -> "GPS précis"
    status == GpsStatus.TRACKING && accuracyM != null -> "GPS ±${accuracyM.roundToInt()} m"
    else -> "Recherche GPS…"
}

/**
 * Point coloré + libellé de qualité du signal : « GPS précis » (success,
 * ≤ 10 m), « GPS ±N m » (warning), « Recherche GPS… » (warning) ou
 * « GPS refusé » (danger).
 */
@Composable
fun GpsStatusPill(
    status: GpsStatus,
    accuracyM: Double?,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val label = gpsStatusLabel(status, accuracyM)
    val color = when {
        status == GpsStatus.DENIED -> colors.danger
        status == GpsStatus.TRACKING && accuracyM != null && accuracyM <= 10 -> colors.success
        else -> colors.warning
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { },
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = label,
            color = colors.textSecondary,
            style = ElanType.label,
        )
    }
}
