package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius

/** Données d'illustration d'un exercice affichées dans la fiche. */
data class ExerciseInfo(
    val name: String,
    /** Glyphe MDI du mouvement (repli sans photo). */
    val icon: String? = null,
    /** Clé d'illustration photo (paire départ → fin). */
    val imageKey: String? = null,
    /** Groupes musculaires sollicités, en pastilles. */
    val muscles: List<String> = emptyList(),
    /** Explication « comment faire ». */
    val howTo: String? = null,
)

/** Pastille de tag (muscle ciblé, matériel) : fond teinte à 12 %, texte 13/700 teinté. */
@Composable
fun TagPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(Radius.pill))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = ElanType.label.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

/**
 * Feuille basse illustrée détaillant un exercice (port de
 * `exercise-info-sheet.tsx`) : illustration départ → fin, nom, « Muscles
 * ciblés » en pastilles, « Exécution ». Glisser vers le bas ou toucher le
 * voile la referme (comportement natif de [ModalBottomSheet]).
 *
 * @param exercise exercice à présenter ; `null` = fiche fermée
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseInfoSheet(exercise: ExerciseInfo?, onClose: () -> Unit) {
    if (exercise == null) return
    val colors = ElanTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = colors.backgroundElement,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            ExerciseIllustration(imageKey = exercise.imageKey, icon = exercise.icon)
            Text(exercise.name, style = ElanType.headline, color = colors.text)
            if (exercise.muscles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Muscles ciblés".uppercase(), style = ElanType.overline, color = colors.textMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.muscles.forEach { TagPill(label = it, color = colors.muscu) }
                    }
                }
            }
            if (!exercise.howTo.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exécution".uppercase(), style = ElanType.overline, color = colors.textMuted)
                    Text(exercise.howTo, style = ElanType.body, color = colors.text)
                }
            }
        }
    }
}
