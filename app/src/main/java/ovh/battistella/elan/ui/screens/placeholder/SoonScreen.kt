package ovh.battistella.elan.ui.screens.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType

/**
 * Destination pas encore portée (muscu, progression, fiche exercice,
 * catalogue) : en-tête avec retour et « Bientôt », pour que la navigation
 * complète existe dès ce jalon.
 */
@Composable
fun SoonScreen(title: String, contentPadding: PaddingValues, onBack: () -> Unit) {
    val colors = ElanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(contentPadding)
            .screenContent()
            .padding(top = 8.dp),
    ) {
        SubScreenHeader(title = title, onBack = onBack)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(stringResource(R.string.common_soon), style = PulseType.headline, color = colors.textSecondary)
        }
    }
}
