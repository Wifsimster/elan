package ovh.battistella.elan.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * « Réduire les animations » : vrai quand l'échelle de durée des animateurs
 * du système est à zéro (Android n'a pas de réglage dédié comme iOS ; la
 * désactivation des animations dans les options d'accessibilité ou de
 * développement passe par cette échelle). Les composants gardent alors leur
 * retour haptique mais suppriment les ressorts d'échelle (confort
 * vestibulaire), comme `useReducedMotion` dans l'app d'origine.
 */
val LocalReducedMotion = compositionLocalOf { false }

/** Lit le réglage système une fois par composition (il change rarement). */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
