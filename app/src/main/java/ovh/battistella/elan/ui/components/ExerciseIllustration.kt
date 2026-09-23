package ovh.battistella.elan.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanGradients
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius

/**
 * Illustration d'un mouvement (port de `exercise-illustration.tsx`) : photos
 * « Départ » → flèche → « Fin » quand elles existent, sinon icône 72 dp sur un
 * dégradé muscu (132 dp). Photos domaine public bundlées : aucun réseau.
 *
 * @param imageKey clé d'illustration (paire départ → fin), résolue localement
 * @param icon glyphe MDI de repli (nom d'origine, résolu via [MdiIcons.byName])
 * @param height hauteur des vignettes photo (le repli garde sa propre hauteur)
 */
@Composable
fun ExerciseIllustration(
    imageKey: String?,
    icon: String?,
    modifier: Modifier = Modifier,
    height: Dp = 176.dp,
) {
    val colors = ElanTheme.colors
    val photos = exerciseImages(imageKey)
    if (photos == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.linearGradient(ElanGradients.muscu, start = Offset.Zero, end = Offset.Infinite),
                    RoundedCornerShape(Radius.lg),
                ),
        ) {
            Icon(
                painter = painterResource(icon?.let(MdiIcons::byName) ?: MdiIcons.Dumbbell),
                contentDescription = null,
                tint = ElanGradients.inkOn(ElanGradients.muscu),
                modifier = Modifier.size(72.dp),
            )
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        HeroPhoto(res = photos.start, label = stringResource(R.string.exercise_illustration_start), height = height, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(MdiIcons.ArrowRight),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
        HeroPhoto(res = photos.end, label = stringResource(R.string.exercise_illustration_end), height = height, modifier = Modifier.weight(1f))
    }
}

/** Une vignette : photo cadrée + libellé d'étape en surimpression. */
@Composable
private fun HeroPhoto(@DrawableRes res: Int, label: String, height: Dp, modifier: Modifier = Modifier) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    val positionDescription = stringResource(R.string.exercise_illustration_a11y, label)
    Box(
        modifier = modifier
            .height(height)
            .background(colors.background, shape)
            .clip(shape)
            .semantics { contentDescription = positionDescription },
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(colors.scrim, RoundedCornerShape(Radius.pill))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            // Blanc fixe : le voile [scrim] est noir dans les deux thèmes.
            Text(label.uppercase(), style = ElanType.overline, color = Color.White)
        }
    }
}
