package ovh.battistella.elan.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.ACTIVITY_TYPES
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.meta
import ovh.battistella.elan.ui.components.EmptyAction
import ovh.battistella.elan.ui.components.EmptyState
import ovh.battistella.elan.ui.components.ElanChip
import ovh.battistella.elan.ui.components.SessionRow
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.components.screenContent
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.common.LinkCard
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import ovh.battistella.elan.ui.theme.forKey

/**
 * Historique des séances : liste paginée (50 par page) avec recherche, filtre
 * par activité et plage de dates. En-tête = titre, lien progression, champ de
 * recherche et puces de filtre, comme le `ListHeaderComponent` d'origine.
 */
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = hiltViewModel(),
    onOpenSession: (Long) -> Unit = {},
    onOpenProgression: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = ElanTheme.colors
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // `onEndReached` : à mi-chemin des derniers éléments visibles, on charge la suite.
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - 1 - (info.visibleItemsInfo.size / 2)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        item(key = "header") {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .screenContent()
                    .padding(bottom = 10.dp),
            ) {
                Text(stringResource(R.string.nav_history), style = ElanType.title, color = colors.text)

                LinkCard(
                    icon = MdiIcons.ChartLine,
                    color = colors.muscu,
                    title = stringResource(R.string.history_progression_title),
                    subtitle = stringResource(R.string.history_progression_subtitle),
                    onClick = onOpenProgression,
                    iconBoxSize = 42.dp,
                )

                SearchField(value = ui.searchInput, onValueChange = viewModel::setSearchInput)

                TypeChips(selected = ui.typeFilter, onSelect = viewModel::setTypeFilter)

                RangeChips(selected = ui.range, onSelect = viewModel::setRange)
            }
        }

        items(count = ui.sessions.size, key = { ui.sessions[it].id }) { index ->
            val s = ui.sessions[index]
            Box(Modifier.screenContent().padding(bottom = 10.dp)) {
                SessionRow(session = s, onClick = { onOpenSession(s.id) })
            }
        }

        if (ui.loaded && !ui.loading && ui.sessions.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.screenContent()) {
                    if (ui.filtered) {
                        EmptyState(
                            icon = MdiIcons.ClipboardTextClockOutline,
                            title = stringResource(R.string.history_empty_filtered_title),
                            subtitle = stringResource(R.string.history_empty_filtered_subtitle),
                        )
                    } else {
                        EmptyState(
                            icon = MdiIcons.ClipboardTextClockOutline,
                            title = stringResource(R.string.history_empty_title),
                            subtitle = stringResource(R.string.history_empty_subtitle),
                            action = EmptyAction(
                                label = stringResource(R.string.history_import_strava),
                                icon = MdiIcons.FileImportOutline,
                                onClick = onOpenSettings,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Champ pilule (h 44, loupe, croix d'effacement). */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = ElanTheme.colors
    val placeholder = stringResource(R.string.history_search_placeholder)
    val clearLabel = stringResource(R.string.history_search_clear)
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.backgroundElement, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            painter = painterResource(MdiIcons.Magnify),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = placeholder },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = TextStyle(fontSize = 15.sp), color = colors.textMuted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            Icon(
                painter = painterResource(MdiIcons.CloseCircle),
                contentDescription = clearLabel,
                tint = colors.textMuted,
                modifier = Modifier
                    .pressableScale(scaleTo = 0.9f, onClick = { onValueChange("") })
                    .size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeChips(
    selected: ActivityType?,
    onSelect: (ActivityType?) -> Unit,
) {
    val colors = ElanTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElanChip(
            label = stringResource(R.string.history_filter_all),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        ACTIVITY_TYPES.forEach { t ->
            ElanChip(
                label = t.meta.shortLabel,
                selected = selected == t,
                color = colors.forKey(t.meta.colorKey),
                onClick = { onSelect(t) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(selected: HistoryRange, onSelect: (HistoryRange) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryRange.entries.forEach { r ->
            ElanChip(label = rangeLabel(r), selected = selected == r, onClick = { onSelect(r) })
        }
    }
}

@Composable
private fun rangeLabel(range: HistoryRange): String = stringResource(
    when (range) {
        HistoryRange.DAYS_7 -> R.string.history_range_7
        HistoryRange.DAYS_30 -> R.string.history_range_30
        HistoryRange.DAYS_90 -> R.string.history_range_90
        HistoryRange.ALL -> R.string.history_filter_all
    },
)
