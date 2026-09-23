package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.GOALS
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.domain.goalSpec
import ovh.battistella.elan.ui.components.ElanCard
import ovh.battistella.elan.ui.components.ElanChip
import ovh.battistella.elan.ui.components.SettingCardHeader
import ovh.battistella.elan.ui.components.SettingStepper
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.haptics.HapticKind
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import kotlin.math.roundToInt

/**
 * Carte Réglages : profil utilisateur (poids, taille, FC max, objectif, sexe).
 * Pilote calories, zones cardio et reps/charge conseillées. Sauvegarde
 * immédiate à chaque changement ; le lien mène au journal de poids.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCard(
    profile: Profile,
    onWeight: (Int) -> Unit,
    onHeight: (Int) -> Unit,
    onMaxHr: (Int) -> Unit,
    onGoal: (TrainingGoal) -> Unit,
    onSex: (Sex?) -> Unit,
    onOpenWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    ElanCard(modifier = modifier) {
        SettingCardHeader(icon = MdiIcons.AccountOutline, color = colors.accent, title = stringResource(R.string.settings_profile_title))
        CardText(stringResource(R.string.settings_profile_intro))

        SettingStepper(label = stringResource(R.string.onboarding_weight), value = profile.weightKg.roundToInt(), unit = "kg", min = 30, max = 200, onChange = onWeight)

        // Journal de poids : historique des pesées + courbe (page dédiée).
        Hairline()
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .pressableScale(haptic = HapticKind.Light, onClick = onOpenWeight)
                .padding(vertical = 10.dp),
        ) {
            Icon(painter = painterResource(MdiIcons.ScaleBathroom), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_weight_journal_title), color = colors.text, style = ElanType.subtitle)
                Text(stringResource(R.string.settings_weight_journal_subtitle), color = colors.textSecondary, style = ElanType.caption)
            }
            Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
        Hairline()

        SettingStepper(label = stringResource(R.string.onboarding_height), value = profile.heightCm.roundToInt(), unit = "cm", min = 120, max = 220, onChange = onHeight)
        SettingStepper(label = stringResource(R.string.onboarding_max_hr), value = profile.maxHr.roundToInt(), unit = "bpm", min = 120, max = 220, onChange = onMaxHr)

        // Objectif d'entraînement : pilote reps/charge conseillées.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.settings_profile_goal_label), style = ElanType.label, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GOALS.forEach { g ->
                    ElanChip(label = g.label, selected = profile.goal == g.id, color = colors.muscu, onClick = { onGoal(g.id) })
                }
            }
            CardText(goalSpec(profile.goal).blurb, muted = true, size = 12)
        }

        // Sexe : affine les charges conseillées (optionnel).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_profile_sex_label), style = ElanType.label, color = colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElanChip(label = stringResource(R.string.settings_sex_unspecified), selected = profile.sex == null, onClick = { onSex(null) })
                ElanChip(label = stringResource(R.string.settings_sex_male), selected = profile.sex == Sex.H, onClick = { onSex(Sex.H) })
                ElanChip(label = stringResource(R.string.settings_sex_female), selected = profile.sex == Sex.F, onClick = { onSex(Sex.F) })
            }
        }
    }
}
