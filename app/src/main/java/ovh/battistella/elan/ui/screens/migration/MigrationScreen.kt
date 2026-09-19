package ovh.battistella.elan.ui.screens.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.data.legacy.MigrationState
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Spacing

/** Fond sombre fixe, identique quel que soit le thème : l'écran précède le thème utilisateur. */
private val MigrationBackground = Color(0xFF0A0C10)
private val MigrationText = Color(0xFFF2F4F8)
private val MigrationTextSecondary = Color(0xFFA0A8B8)
private val MigrationAccent = Color(0xFF5B7CFF)
private val MigrationDanger = Color(0xFFFF5B6E)

/**
 * Écran plein cadre affiché tant que la reprise des anciennes données n'est
 * pas réglée. [state] ne vaut jamais `Ready` ici (l'activité monte alors
 * l'interface normale). En [MigrationState.Pending] on ne montre que le fond :
 * la vérification dure quelques millisecondes, un titre clignoterait.
 */
@Composable
fun MigrationScreen(
    state: MigrationState,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MigrationBackground)
            .safeDrawingPadding()
            .padding(Spacing.four),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            MigrationState.Pending, MigrationState.Ready -> Unit
            is MigrationState.Running -> RunningContent(state)
            is MigrationState.Failed -> FailedContent(state, onRetry, onSkip)
        }
    }
}

@Composable
private fun RunningContent(state: MigrationState.Running) {
    Text(
        text = stringResource(R.string.migration_title),
        style = PulseType.title,
        color = MigrationText,
        textAlign = TextAlign.Center,
    )
    val progressModifier = Modifier
        .padding(top = Spacing.four)
        .widthIn(max = 360.dp)
        .fillMaxWidth()
    if (state.total > 0) {
        LinearProgressIndicator(
            progress = { (state.copied.toFloat() / state.total).coerceIn(0f, 1f) },
            modifier = progressModifier,
            color = MigrationAccent,
        )
        Text(
            text = stringResource(R.string.migration_progress, state.copied, state.total),
            style = PulseType.caption,
            color = MigrationTextSecondary,
            modifier = Modifier.padding(top = Spacing.two),
        )
    } else {
        LinearProgressIndicator(modifier = progressModifier, color = MigrationAccent)
    }
    Text(
        text = stringResource(R.string.migration_keep_open),
        style = PulseType.body,
        color = MigrationTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.four),
    )
}

@Composable
private fun FailedContent(
    state: MigrationState.Failed,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    // 0 = aucun dialogue, 1 = première confirmation, 2 = seconde.
    var confirmStep by rememberSaveable { mutableIntStateOf(0) }

    Text(
        text = stringResource(R.string.migration_failed_title),
        style = PulseType.title,
        color = MigrationText,
        textAlign = TextAlign.Center,
    )
    Text(
        text = state.reason,
        style = PulseType.body,
        color = MigrationDanger,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.three),
    )
    Text(
        text = stringResource(R.string.migration_failed_hint),
        style = PulseType.body,
        color = MigrationTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.three),
    )
    if (state.canRetry) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = Spacing.five)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Text(stringResource(R.string.migration_retry))
        }
    }
    OutlinedButton(
        onClick = { confirmStep = 1 },
        modifier = Modifier
            .padding(top = if (state.canRetry) Spacing.two else Spacing.five)
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
    ) {
        Text(stringResource(R.string.migration_skip), color = MigrationText)
    }

    if (confirmStep == 1) {
        SkipDialog(
            title = stringResource(R.string.migration_skip_confirm_title),
            text = stringResource(R.string.migration_skip_confirm_text),
            onConfirm = { confirmStep = 2 },
            onDismiss = { confirmStep = 0 },
        )
    } else if (confirmStep == 2) {
        SkipDialog(
            title = stringResource(R.string.migration_skip_confirm_again_title),
            text = stringResource(R.string.migration_skip_confirm_again_text),
            onConfirm = {
                confirmStep = 0
                onSkip()
            },
            onDismiss = { confirmStep = 0 },
        )
    }
}

@Composable
private fun SkipDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.migration_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.migration_cancel)) }
        },
    )
}
