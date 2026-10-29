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
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

@Composable
fun HealthRationaleScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val colors = ElanTheme.colors
    val body = ElanType.body
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
        Text(stringResource(R.string.health_rationale_writes_title), style = ElanType.headline, color = colors.text)
        Text(stringResource(R.string.health_rationale_writes), style = body, color = colors.textSecondary)
        Text(stringResource(R.string.health_rationale_reads_title), style = ElanType.headline, color = colors.text)
        Text(stringResource(R.string.health_rationale_reads), style = body, color = colors.textSecondary)
        Text(stringResource(R.string.health_rationale_control), style = body, color = colors.textSecondary)
    }
}
