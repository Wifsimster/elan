package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.fmtKg
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType
import ovh.battistella.elan.ui.theme.Radius
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Valeur saisie au clavier → valeur retenue, ou `null` si vide / invalide (on
 * garde alors l'ancienne). Virgule acceptée ; arrondi au dixième en décimal,
 * à l'entier sinon ; jamais sous [min].
 */
internal fun parseStepperInput(text: String, decimal: Boolean, min: Double): Double? {
    val parsed = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!parsed.isFinite()) return null
    val rounded = if (decimal) (parsed * 10).roundToInt() / 10.0 else parsed.roundToInt().toDouble()
    return max(min, rounded)
}

/**
 * Incrémenteur d'une série muscu (reps ou charge) : boutons −/+ par paliers,
 * et saisie directe en tapant la valeur (champ en ligne, clavier numérique,
 * virgule acceptée). Reps : pas 1, min 1 ; charge : pas 2,5, min 0, décimal.
 */
@Composable
fun Stepper(
    value: Double,
    suffix: String,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    min: Double = 0.0,
    /** Autorise la saisie décimale (poids : réglage au kg, voire 0,5 kg près). */
    decimal: Boolean = false,
) {
    val colors = ElanTheme.colors
    val shape = RoundedCornerShape(Radius.sm)
    val display = fmtKg(value)
    val valueDescription = stringResource(R.string.stepper_value_a11y, display, suffix)

    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }

    val commit = {
        if (editing) {
            editing = false
            parseStepperInput(text.text, decimal, min)?.let(onChange)
        }
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(colors.background, shape)
            .border(1.dp, colors.border, shape)
            .clip(shape)
            .padding(4.dp),
    ) {
        StepButton(
            icon = MdiIcons.Minus,
            description = stringResource(R.string.stepper_decrease_a11y, suffix),
            onClick = { onChange(max(min, value - step)) },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (editing) Modifier
                    else Modifier.pressableScale(scaleTo = 0.94f, onClick = {
                        val raw = display.replace(',', '.')
                        text = TextFieldValue(raw, TextRange(0, raw.length))
                        editing = true
                    }),
                )
                .semantics { contentDescription = valueDescription },
        ) {
            // Valeur chiffrée : condensé tabulaire, couleur et centrage portés par le style (champ de saisie).
            val valueStyle = ElanType.metricSm.copy(color = colors.text, textAlign = TextAlign.Center)
            if (editing) {
                // Le premier événement de focus est « non focalisé » (avant la
                // demande) : on ne valide qu'à une vraie perte de focus.
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = valueStyle,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) hadFocus = true else if (hadFocus) commit()
                        },
                )
            } else {
                Text(display, style = valueStyle, maxLines = 1)
            }
            Text(suffix, style = ElanType.micro, color = colors.textSecondary, maxLines = 1)
        }
        StepButton(
            icon = MdiIcons.Plus,
            description = stringResource(R.string.stepper_increase_a11y, suffix),
            onClick = { onChange(value + step) },
        )
    }
}

@Composable
private fun StepButton(icon: Int, description: String, onClick: () -> Unit) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = ElanTheme.colors.text,
        modifier = Modifier
            .pressableScale(scaleTo = 0.88f, onClick = onClick)
            .padding(8.dp)
            .size(18.dp),
    )
}
