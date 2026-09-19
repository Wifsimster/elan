package ovh.battistella.elan.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.SessionRecord
import ovh.battistella.elan.domain.Effort
import ovh.battistella.elan.domain.MuscuSet
import ovh.battistella.elan.domain.Session
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.ShareCard
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius

/**
 * Aperçu de la carte de partage : la carte est dessinée dans un
 * `GraphicsLayer` enregistré à chaque frame ; « Partager » la convertit en
 * bitmap (exactement ce qui est à l'écran) et la transmet à [onShare].
 */
@Composable
fun SharePreviewDialog(
    session: Session,
    points: List<TrackPoint>,
    sets: List<MuscuSet>,
    records: List<SessionRecord>,
    effort: Effort,
    sharing: Boolean,
    onShare: (ImageBitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ElanTheme.colors
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .background(colors.backgroundElement, RoundedCornerShape(Radius.xl))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.share_preview_title), style = PulseType.headline, color = colors.text)
            ShareCard(
                session = session,
                points = points,
                sets = sets,
                records = records,
                effort = effort,
                modifier = Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            )
            PulseButton(
                title = stringResource(R.string.share_action),
                icon = MdiIcons.ShareVariant,
                loading = sharing,
                onClick = { scope.launch { onShare(layer.toImageBitmap()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            PulseButton(
                title = stringResource(R.string.common_cancel),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
