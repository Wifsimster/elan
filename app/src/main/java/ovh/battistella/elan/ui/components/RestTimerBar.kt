package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.formatDuration
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.haptics.rememberHaptics
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.sound.Sounds
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Elevation
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.ceil
import kotlin.math.max

/** Auto-fermeture de la barre une fois le repos terminé (ms). */
const val REST_LINGER_MS = 5_000L

/** Cadence de rafraîchissement du décompte (ms). */
const val REST_TICK_MS = 250L

/**
 * Minuteur de repos inter-séries (port de `rest-timer.tsx`) : barre flottante
 * qui décompte le temps de récupération, démarrée quand une série est cochée.
 * À zéro : haptique « succès » + carillon, l'icône passe au `check-circle`,
 * « Repos » devient « Repos terminé », puis fermeture automatique après
 * [REST_LINGER_MS]. Les pilules −15/+15 décalent la fin ET signalent le delta
 * pour que l'appelant mémorise la DURÉE préférée (pas le temps restant).
 *
 * L'appelant remonte le composant à chaque (re)programmation (`key(endsAt)`)
 * pour que l'état interne reparte à neuf. 100 % local.
 *
 * @param endsAt horodatage (ms epoch) de fin du repos, `null` = aucun repos
 * @param onChange nouvel horodatage de fin, ou `null` pour fermer
 * @param onAdjustPreference ajustement ±Δs de la durée souhaitée (−15 / +15)
 * @param now horloge (injectable en test)
 */
@Composable
fun RestTimerBar(
    endsAt: Long?,
    onChange: (Long?) -> Unit,
    onAdjustPreference: (Int) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Long = System::currentTimeMillis,
) {
    if (endsAt == null) return
    val colors = ElanTheme.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()

    var current by remember(endsAt) { mutableLongStateOf(now()) }
    // Un seul retour haptique + carillon par repos.
    val fired = remember(endsAt) { arrayOf(false) }

    // Tic d'horloge tant que la barre est affichée ; à zéro, retour + son ;
    // fermeture automatique après le délai de présence.
    LaunchedEffect(endsAt) {
        while (isActive) {
            current = now()
            if (current >= endsAt && !fired[0]) {
                fired[0] = true
                haptics(HapticKind.Success)
                Sounds.restDone(context)
            }
            if (current >= endsAt + REST_LINGER_MS) {
                onChange(null)
                break
            }
            delay(REST_TICK_MS)
        }
    }

    val remainingSec = max(0L, ceil((endsAt - current) / 1000.0).toLong()).toInt()
    val done = remainingSec == 0

    // Décale la fin à partir du temps restant courant (ou de maintenant si
    // terminé), et remonte le delta pour la préférence.
    val adjust = { deltaSec: Int ->
        val base = max(endsAt, current)
        onChange(max(current + 1_000, base + deltaSec * 1_000L))
        onAdjustPreference(deltaSec)
    }

    val shape = RoundedCornerShape(Radius.lg)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .shadow(Elevation.md, shape)
            .background(colors.surfaceHigh, shape)
            .clip(shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(if (done) MdiIcons.CheckCircle else MdiIcons.TimerSand),
            contentDescription = null,
            tint = if (done) colors.success else colors.muscu,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (if (done) "Repos terminé" else "Repos").uppercase(),
                style = PulseType.overline,
                color = colors.textSecondary,
            )
            Text(
                formatDuration(remainingSec),
                style = PulseType.metric.copy(fontSize = 26.sp),
                color = if (done) colors.success else colors.text,
            )
        }
        RestPill(label = "−15", description = stringResource(R.string.rest_timer_minus_a11y), onClick = { adjust(-15) })
        RestPill(label = "+15", description = stringResource(R.string.rest_timer_plus_a11y), onClick = { adjust(15) })
        Icon(
            painter = painterResource(MdiIcons.CloseCircle),
            contentDescription = stringResource(R.string.rest_timer_close_a11y),
            tint = colors.textSecondary,
            modifier = Modifier
                .pressableScale(scaleTo = 0.85f, onClick = { onChange(null) })
                .padding(4.dp)
                .size(26.dp),
        )
    }
}

@Composable
private fun RestPill(label: String, description: String, onClick: () -> Unit) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .semantics { contentDescription = description }
            .pressableScale(onClick = onClick)
            .height(34.dp)
            .widthIn(min = 44.dp)
            .background(colors.backgroundElement, shape)
            .border(1.dp, colors.border, shape)
            .clip(shape)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.text,
        )
    }
}
