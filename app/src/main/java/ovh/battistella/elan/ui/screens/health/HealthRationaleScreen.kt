package ovh.battistella.elan.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType

/**
 * Justification des permissions Health Connect, ouverte par le système
 * (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`) depuis l'écran des
 * permissions ou l'alias `ViewPermissionUsageActivity`. Texte seul : ce
 * qu'Élan écrit, pourquoi, et ce qu'il ne lit jamais.
 */
@Composable
fun HealthRationaleScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val colors = ElanTheme.colors
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(contentPadding)
            .screenContent()
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SubScreenHeader(title = stringResource(R.string.health_rationale_title), onBack = onBack)
        Text(stringResource(R.string.health_rationale_intro), style = body, color = colors.text)
        Text(stringResource(R.string.health_rationale_writes_title), style = PulseType.headline, color = colors.text)
        Text(stringResource(R.string.health_rationale_writes), style = body, color = colors.textSecondary)
        Text(stringResource(R.string.health_rationale_reads_title), style = PulseType.headline, color = colors.text)
        Text(stringResource(R.string.health_rationale_reads), style = body, color = colors.textSecondary)
        Text(stringResource(R.string.health_rationale_control), style = body, color = colors.textSecondary)
    }
}
