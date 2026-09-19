package ovh.battistella.elan.ui.screens.migration

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.R
import ovh.battistella.elan.data.legacy.MigrationState
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class MigrationScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int) = context.getString(res)

    @Test
    fun `en cours titre avancement et consigne`() {
        compose.setContent {
            ElanTheme { MigrationScreen(MigrationState.Running(2_500, 10_000), onRetry = {}, onSkip = {}) }
        }
        compose.onNodeWithText(text(R.string.migration_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.migration_keep_open)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.migration_progress, 2_500L, 10_000L)).assertIsDisplayed()
    }

    @Test
    fun `échec rejouable réessayer déclenche le rappel`() {
        var retries = 0
        compose.setContent {
            ElanTheme {
                MigrationScreen(MigrationState.Failed("disque plein", canRetry = true), onRetry = { retries++ }, onSkip = {})
            }
        }
        compose.onNodeWithText("disque plein").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.migration_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `échec définitif pas de réessayer`() {
        compose.setContent {
            ElanTheme {
                MigrationScreen(MigrationState.Failed("version 8", canRetry = false), onRetry = {}, onSkip = {})
            }
        }
        compose.onNodeWithText(text(R.string.migration_retry)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.migration_skip)).assertIsDisplayed()
    }

    @Test
    fun `continuer sans données exige deux confirmations`() {
        var skips = 0
        compose.setContent {
            ElanTheme {
                MigrationScreen(MigrationState.Failed("boum", canRetry = true), onRetry = {}, onSkip = { skips++ })
            }
        }
        compose.onNodeWithText(text(R.string.migration_skip)).performClick()
        compose.onNodeWithText(text(R.string.migration_skip_confirm_title)).assertIsDisplayed()
        // Annuler au premier dialogue : rien ne se passe.
        compose.onNodeWithText(text(R.string.migration_cancel)).performClick()
        compose.onNodeWithText(text(R.string.migration_skip_confirm_title)).assertDoesNotExist()
        assertEquals(0, skips)

        compose.onNodeWithText(text(R.string.migration_skip)).performClick()
        compose.onNodeWithText(text(R.string.migration_confirm)).performClick()
        compose.onNodeWithText(text(R.string.migration_skip_confirm_again_title)).assertIsDisplayed()
        assertEquals(0, skips)
        compose.onNodeWithText(text(R.string.migration_confirm)).performClick()
        assertEquals(1, skips)
        compose.onNodeWithText(text(R.string.migration_skip_confirm_again_title)).assertDoesNotExist()
    }
}
