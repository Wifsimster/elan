package ovh.battistella.elan.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Spacing

/** Réglages — placeholder jusqu'au jalon des écrans. */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    Text(
        text = stringResource(R.string.nav_settings),
        style = PulseType.title,
        color = ElanTheme.colors.text,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Spacing.three),
    )
}
