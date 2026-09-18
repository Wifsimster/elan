package ovh.battistella.elan.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ovh.battistella.elan.R
import ovh.battistella.elan.data.repository.SessionRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.TrackPoint
import ovh.battistella.elan.domain.isGpsActivity
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.ui.components.RouteCanvas
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey
import javax.inject.Inject

data class SessionMapUi(
    val loading: Boolean = true,
    val type: ActivityType = ActivityType.VELO,
    val points: List<TrackPoint> = emptyList(),
)

@HiltViewModel
class SessionMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val sessionId = sessionIdArg(savedStateHandle)

    private val _ui = MutableStateFlow(SessionMapUi())
    val ui: StateFlow<SessionMapUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = sessions.getSession(sessionId)
            val points = if (s != null && isGpsActivity(s.type)) sessions.getTrackPoints(sessionId) else emptyList()
            _ui.value = SessionMapUi(loading = false, type = s?.type ?: ActivityType.VELO, points = points)
        }
    }
}

/**
 * Carte plein écran d'une sortie : le tracé remplit l'écran et devient
 * explorable (pinch-zoom, pan, double-tap). Bouton retour flottant en haut à
 * gauche, sous l'encoche.
 */
@Composable
fun SessionMapScreen(
    contentPadding: PaddingValues,
    viewModel: SessionMapViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val color = colors.forKey(ui.type.meta.colorKey)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        when {
            ui.loading -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            ui.points.size >= 2 -> RouteCanvas(points = ui.points, color = color, interactive = true, fill = true)
            else -> Text(
                stringResource(R.string.map_empty),
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(contentPadding)
                .padding(top = 8.dp, start = 16.dp)
                .pressableScale(scaleTo = 0.88f, onClick = onBack)
                .shadow(Elevation.md, RoundedCornerShape(Radius.pill))
                .background(colors.backgroundElement, RoundedCornerShape(Radius.pill))
                .size(44.dp),
        ) {
            Icon(
                painter = painterResource(MdiIcons.ArrowLeft),
                contentDescription = stringResource(R.string.map_back),
                tint = colors.text,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
