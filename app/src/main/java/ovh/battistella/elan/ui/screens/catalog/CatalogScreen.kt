package ovh.battistella.elan.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.components.ExerciseCatalog
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.screens.common.SubScreenHeader
import ovh.battistella.elan.ui.theme.ElanTheme

/**
 * Magasin d'exercices hors séance : parcourir, voir le détail et les charges
 * conseillées, puis démarrer une séance avec l'exercice choisi (`muscu?add=`).
 */
@Composable
fun CatalogScreen(
    contentPadding: PaddingValues,
    viewModel: CatalogViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onAddToSession: (String) -> Unit = {},
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding())
            .screenContent(),
    ) {
        SubScreenHeader(title = stringResource(R.string.catalog_title), onBack = onBack, modifier = Modifier.padding(bottom = 12.dp))
        ExerciseCatalog(
            profile = profile,
            onPick = { onAddToSession(it.id) },
            addLabel = stringResource(R.string.catalog_add_to_session),
            modifier = Modifier.weight(1f),
        )
    }
}
