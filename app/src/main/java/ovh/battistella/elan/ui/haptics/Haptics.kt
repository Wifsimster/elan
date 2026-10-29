package ovh.battistella.elan.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticKind {
    Selection,
    Light,
    Medium,
    Heavy,
    Success,
    Error,
}

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

@Composable
fun rememberHaptics(): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> performHaptic(view, kind) } }
}
