package ovh.battistella.elan.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.R
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
class ElanNavigationTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun label(res: Int) = context.getString(res)

    @Test
    fun rootShowsTheThreeTabs() {
        compose.setContent { ElanTheme { ElanRoot() } }

        // L'accueil est la destination de départ : son titre ET son onglet.
        compose.onAllNodesWithText(label(R.string.nav_home)).assertCountEquals(2)
        // Les deux autres n'existent encore que comme onglets.
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(1)
        compose.onAllNodesWithText(label(R.string.nav_settings)).assertCountEquals(1)
    }

    @Test
    fun tappingATabOpensItsScreen() {
        compose.setContent { ElanTheme { ElanRoot() } }

        compose.onNodeWithText(label(R.string.nav_history)).performClick()
        compose.waitForIdle()

        // Titre de l'écran + onglet ; l'accueil n'est plus affiché.
        compose.onAllNodesWithText(label(R.string.nav_history)).assertCountEquals(2)
        compose.onAllNodesWithText(label(R.string.nav_home)).assertCountEquals(1)
    }
}
