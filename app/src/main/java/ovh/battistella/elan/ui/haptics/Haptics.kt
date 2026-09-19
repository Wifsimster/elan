package ovh.battistella.elan.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Retours haptiques du design system PULSE (docs/port-spec/02-interface.md
 * §5). Toute interaction marquante en déclenche un ; l'échec (appareil sans
 * moteur, réglage système coupé) est silencieux.
 */
enum class HapticKind {
    /** Appui sur un contrôle secondaire, changement de sélection (chips, steppers). */
    Selection,
    /** Appui sur une action principale. */
    Light,
    /** Démarrage / bascule d'état d'effort (start, pause). */
    Medium,
    /** Action lourde ou destructrice confirmée. */
    Heavy,
    /** Séance enregistrée, série cochée, repos fini, retype. */
    Success,
    /** Erreur, action refusée. */
    Error,
}

/**
 * Joue le retour haptique [kind] via la vue hôte. `CONFIRM` / `REJECT`
 * n'existent qu'à partir d'Android 11 : en dessous, le succès retombe sur un
 * simple tap clavier et l'erreur sur le motif d'appui long (le plus appuyé
 * des motifs disponibles).
 */
fun performHaptic(view: View, kind: HapticKind) {
    val constant = when (kind) {
        HapticKind.Selection -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticKind.Light -> HapticFeedbackConstants.KEYBOARD_TAP
        HapticKind.Medium -> HapticFeedbackConstants.LONG_PRESS
        HapticKind.Heavy -> HapticFeedbackConstants.LONG_PRESS
        HapticKind.Success ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        HapticKind.Error ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
    }
    runCatching { view.performHapticFeedback(constant) }
}

/** Fonction prête à l'emploi, liée à la vue hôte de la composition. */
@Composable
fun rememberHaptics(): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> performHaptic(view, kind) } }
}
