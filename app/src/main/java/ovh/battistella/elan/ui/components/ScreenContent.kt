package ovh.battistella.elan.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.MaxContentWidth

/**
 * Mise en page standard d'un contenu d'écran (`use-screen-layout` dans l'app
 * d'origine) : insets système gauche/droite + marge horizontale, largeur
 * plafonnée à [MaxContentWidth] et centrée sur tablette / paysage.
 */
@Composable
fun Modifier.screenContent(horizontal: Dp = 16.dp): Modifier = this
    .fillMaxWidth()
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = MaxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = horizontal)
