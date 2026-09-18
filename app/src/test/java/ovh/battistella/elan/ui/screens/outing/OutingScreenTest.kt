package ovh.battistella.elan.ui.screens.outing

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import ovh.battistella.elan.R
import ovh.battistella.elan.domain.GpsStatus
import ovh.battistella.elan.domain.LatLon
import ovh.battistella.elan.ui.screens.FakeCadencePort
import ovh.battistella.elan.ui.screens.FakeOutingPort
import ovh.battistella.elan.ui.theme.ElanTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class OutingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int) = context.getString(res)

    private fun vm(type: String, port: FakeOutingPort, cadence: FakeCadencePort = FakeCadencePort()) =
        OutingViewModel(SavedStateHandle(mapOf("type" to type)), port, cadence)

    @Test
    fun `idle - en-tête, chrono et bouton Démarrer, permission accordée déclenche begin`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val port = FakeOutingPort()
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("velo", port)) } }
        compose.waitForIdle()

        compose.onNodeWithText("Vélo").assertIsDisplayed()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_speed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_max_speed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_pause)).assertDoesNotExist()
        // Pas de pastille GPS ni de carte avant le départ.
        compose.onNodeWithText("Recherche GPS…").assertDoesNotExist()

        compose.onNodeWithText(text(R.string.outing_start)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("begin:velo"), port.calls)
    }

    @Test
    fun `course - allure et meilleure allure, tuiles vélo absentes`() {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Active, speedKmh = 12.0, maxSpeedKmh = 15.0, gpsStatus = GpsStatus.TRACKING, accuracyM = 5.0, wheelSpeedKmh = 20.0, cadenceRpm = 80))
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("course", port, FakeCadencePort(hasSensor = true))) } }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.outing_pace)).assertIsDisplayed()
        compose.onNodeWithText("5:00").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_best_pace)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_wheel_speed)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.outing_cadence)).assertDoesNotExist()
        compose.onNodeWithText("GPS précis").assertIsDisplayed()
    }

    @Test
    fun `vélo actif - stats, capteurs, carte live et contrôles`() {
        val path = listOf(LatLon(48.85, 2.35), LatLon(48.851, 2.351), LatLon(48.852, 2.352))
        val port = FakeOutingPort(
            OutingUi(
                phase = OutingPhase.Active,
                elapsedSec = 754,
                distanceM = 12_400.0,
                speedKmh = 27.3,
                bpm = 143,
                cadenceRpm = 88,
                wheelSpeedKmh = 27.9,
                elevationGainM = 57.4,
                caloriesLive = 312.2,
                gpsStatus = GpsStatus.TRACKING,
                accuracyM = 22.0,
                livePath = path,
            ),
        )
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("velo", port, FakeCadencePort(hasSensor = true))) } }
        compose.waitForIdle()

        compose.onNodeWithText("12:34").assertIsDisplayed()
        compose.onNodeWithText("27,3").assertIsDisplayed()
        compose.onNodeWithText("12,4").assertIsDisplayed()
        compose.onNodeWithText("143").assertIsDisplayed()
        compose.onNodeWithText("88").assertIsDisplayed()
        compose.onNodeWithText("27,9").assertIsDisplayed()
        compose.onNodeWithText("57").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("312").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GPS ±22 m").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tracé GPS de la sortie en cours").assertExists()

        compose.onNodeWithText(text(R.string.outing_pause)).performClick()
        compose.onNodeWithText(text(R.string.outing_finish)).performClick()
        compose.onNodeWithText(text(R.string.outing_finish_title)).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.outing_finish)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(listOf("pause", "finish"), port.calls)
    }

    @Test
    fun `en pause - Reprendre et cadre d'attente sans tracé`() {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Paused, gpsStatus = GpsStatus.REQUESTING))
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("velo", port)) } }
        compose.waitForIdle()

        compose.onNodeWithText("Recherche du signal GPS…").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.outing_resume)).performClick()
        assertEquals(listOf("resume"), port.calls)
    }

    @Test
    fun `abandon via la croix - confirmation puis sortie`() {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Active))
        var exits = 0
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("marche", port), onExit = { exits++ }) } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(text(R.string.outing_quit)).performClick()
        compose.onNodeWithText(text(R.string.outing_discard_title)).assertIsDisplayed()
        // « Continuer » referme sans rien faire.
        compose.onNodeWithText(text(R.string.common_continue)).performClick()
        compose.onNodeWithText(text(R.string.outing_discard_title)).assertDoesNotExist()
        assertEquals(0, exits)
        assertTrue(port.calls.isEmpty())

        compose.onNodeWithContentDescription(text(R.string.outing_quit)).performClick()
        compose.onNodeWithText(text(R.string.outing_abandon)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("discard"), port.calls)
        assertEquals(1, exits)
    }

    @Test
    fun `échec d'enregistrement - Abandonner et Réessayer, alerte`() {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Active))
        var exits = 0
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("velo", port), onExit = { exits++ }) } }
        compose.waitForIdle()

        port.update { copy(phase = OutingPhase.SaveFailed, errorMessage = "boum") }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.outing_save_failed_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.common_ok)).performClick()
        compose.onNodeWithText(text(R.string.outing_save_failed_title)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.outing_pause)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.outing_retry)).performClick()
        assertEquals(listOf("retrySave"), port.calls)
        compose.onNodeWithText(text(R.string.outing_abandon)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("retrySave", "discard"), port.calls)
        assertEquals(1, exits)
    }

    @Test
    fun `séance enregistrée - navigation vers le détail`() {
        val port = FakeOutingPort(OutingUi(phase = OutingPhase.Saving))
        var saved: Long? = null
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm("velo", port), onSaved = { saved = it }) } }
        compose.waitForIdle()
        port.update { copy(phase = OutingPhase.Idle, savedSessionId = 9) }
        compose.waitForIdle()
        assertEquals(9L, saved)
    }

    @Test
    fun `permission refusée - alerte Localisation refusée`() {
        val port = FakeOutingPort()
        val vm = vm("velo", port)
        compose.setContent { ElanTheme { OutingScreen(contentPadding = PaddingValues(), viewModel = vm) } }
        compose.waitForIdle()
        vm.onLocationPermission(fine = false, coarse = false)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.outing_denied_title)).assertIsDisplayed()
        vm.onLocationPermission(fine = false, coarse = true)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.outing_coarse_title)).assertIsDisplayed()
    }
}
