package ovh.battistella.elan.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.GOALS
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.ui.components.ButtonVariant
import ovh.battistella.elan.ui.components.PulseButton
import ovh.battistella.elan.ui.components.PulseChip
import ovh.battistella.elan.ui.components.SettingStepper
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.PulseType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.roundToInt

/**
 * Accueil premier lancement (100 % local). Capture poids, taille, FC max et
 * objectif — sans eux, les calories, les zones cardio et les charges
 * conseillées tournent sur des valeurs par défaut silencieusement
 * approximatives dès la première séance. Rassure aussi sur le fait que rien
 * ne quitte l'appareil. Non fermable autrement que par ses boutons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingSheet(
    initial: Profile,
    onDone: (weightKg: Int, heightCm: Int, maxHr: Int, goal: TrainingGoal) -> Unit,
    onRestore: () -> Unit,
) {
    val colors = ElanTheme.colors
    var weightKg by rememberSaveable { mutableIntStateOf(initial.weightKg.roundToInt()) }
    var heightCm by rememberSaveable { mutableIntStateOf(initial.heightCm.roundToInt()) }
    var maxHr by rememberSaveable { mutableIntStateOf(initial.maxHr.roundToInt()) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Scrim + colonne centrée quand le contenu tient, défilable sinon
        // (paysage court). Insets latéraux pour l'encoche.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .background(colors.backgroundElement, RoundedCornerShape(Radius.xl))
                    .padding(24.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(colors.accent.copy(alpha = 0.13f), RoundedCornerShape(Radius.lg)),
                ) {
                    Icon(
                        painter = painterResource(MdiIcons.HandWave),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.onboarding_title), style = PulseType.title, color = colors.text)
                    Text(stringResource(R.string.onboarding_subtitle), style = PulseType.body, color = colors.textSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(MdiIcons.LockOutline),
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_privacy),
                        style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.onboarding_profile_hint), style = PulseType.label, color = colors.textSecondary)
                    SettingStepper(
                        label = stringResource(R.string.onboarding_weight),
                        value = weightKg,
                        unit = "kg",
                        min = 30,
                        max = 200,
                        onChange = { weightKg = it },
                    )
                    SettingStepper(
                        label = stringResource(R.string.onboarding_height),
                        value = heightCm,
                        unit = "cm",
                        min = 120,
                        max = 220,
                        onChange = { heightCm = it },
                    )
                    SettingStepper(
                        label = stringResource(R.string.onboarding_max_hr),
                        value = maxHr,
                        unit = "bpm",
                        min = 120,
                        max = 220,
                        onChange = { maxHr = it },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.onboarding_goal_hint), style = PulseType.label, color = colors.textSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GOALS.forEach { g ->
                            PulseChip(label = g.label, selected = goal == g.id, onClick = { goal = g.id })
                        }
                    }
                }

                Text(stringResource(R.string.onboarding_editable), style = PulseType.caption, color = colors.textMuted)

                PulseButton(
                    title = stringResource(R.string.onboarding_go),
                    icon = MdiIcons.ArrowRight,
                    onClick = { onDone(weightKg, heightCm, maxHr, goal) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Réinstallation : la base est vide, on propose de tout recharger
                // depuis le serveur plutôt que de repartir de zéro.
                PulseButton(
                    title = stringResource(R.string.onboarding_restore),
                    icon = MdiIcons.CloudDownloadOutline,
                    variant = ButtonVariant.Ghost,
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
