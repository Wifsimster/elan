package ovh.battistella.elan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.Radius

/**
 * Champ texte étiqueté d'un réglage (endpoint S3, bucket, URL de carte…).
 * Désactive auto-capitalisation et correction (saisie d'identifiants/URLs).
 * Un champ [secret] propose « Afficher » : une clé tapée à la main masquée
 * cache ses fautes de frappe, et l'erreur serveur qui en découle
 * (SignatureDoesNotMatch) n'oriente pas vers la saisie.
 */
@Composable
fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = ElanTheme.colors
    var revealed by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.sm)
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, style = labelStyle, color = colors.textSecondary)
            if (secret) {
                val toggleLabel = if (revealed) "Masquer" else "Afficher"
                Text(
                    text = toggleLabel,
                    style = labelStyle,
                    color = colors.accent,
                    modifier = Modifier
                        .semantics { contentDescription = "$toggleLabel $label" }
                        .pressableScale(haptic = null) { revealed = !revealed }
                        .padding(4.dp),
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation =
                if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (secret) KeyboardType.Password else keyboardType,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.backgroundSelected, shape)
                        .border(1.dp, colors.border, shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = TextStyle(fontSize = 15.sp), color = colors.textMuted)
                    }
                    inner()
                }
            },
        )
    }
}
