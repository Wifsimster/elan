package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.testBleManagers
import ovh.battistella.elan.ui.theme.ElanTheme
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val reminders = FakeRemindersPort()
    private val backup = FakeBackupPort()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun setScreen(healthSupported: Boolean, onOpenWeight: () -> Unit = {}) {
        val vm = SettingsViewModel(
            repos.settings, repos.sessions, reminders, FakeHealthConnectPort(isSupported = healthSupported),
            FakeMapStylePort(), FakeExportPort(), FakeStravaImportPort(), Clock.systemUTC(), context,
        )
        val (hr, csc) = testBleManagers(repos.settings)
        val sensors = SensorsViewModel(hr, csc)
        val backupVm = BackupViewModel(backup, repos.settings)
        compose.setContent {
            ElanTheme {
                SettingsScreen(
                    contentPadding = PaddingValues(),
                    viewModel = vm,
                    sensorsViewModel = sensors,
                    backupViewModel = backupVm,
                    onOpenWeight = onOpenWeight,
                )
            }
        }
        compose.waitForIdle()
    }

    private val cardTitles = listOf(
        R.string.settings_hr_title,
        R.string.settings_csc_title,
        R.string.settings_map_title,
        R.string.settings_health_title,
        R.string.settings_profile_title,
        R.string.settings_goals_title,
        R.string.settings_plan_title,
        R.string.settings_progression_title,
        R.string.settings_notif_title,
        R.string.settings_data_title,
        R.string.settings_export_card_title,
        R.string.settings_strava_title,
        R.string.settings_backup_title,
    )

    @Test
    fun `les 13 cartes sont présentes dans l'ordre, Health Connect comprise`() {
        setScreen(healthSupported = true)

        compose.onNodeWithText(text(R.string.nav_settings)).assertIsDisplayed()
        val tops = cardTitles.map { res ->
            compose.onNodeWithText(text(res)).assertExists().getUnclippedBoundsInRoot().top.value
        }
        assertEquals(tops, tops.sorted())
        assertEquals(13, tops.toSet().size)
    }

    @Test
    fun `Health Connect masquée quand non supportée`() {
        setScreen(healthSupported = false)

        compose.onNodeWithText(text(R.string.settings_health_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_map_title)).assertExists()
        compose.onNodeWithText(text(R.string.settings_profile_title)).assertExists()
    }

    @Test
    fun `capteurs sans appareil - états au repos et boutons de recherche`() {
        setScreen(healthSupported = false)

        compose.onNodeWithText(text(R.string.settings_hr_idle)).assertExists()
        compose.onNodeWithText(text(R.string.settings_hr_scan)).assertExists()
        compose.onNodeWithText(text(R.string.settings_csc_scan)).assertExists()
        // Preset de pneu par défaut.
        compose.onNodeWithText("700×25c").assertExists()
    }

    @Test
    fun `profil - stepper persisté, lien vers le journal de poids`() {
        var opened = 0
        setScreen(healthSupported = false, onOpenWeight = { opened++ })

        compose.onNodeWithContentDescription("Augmenter ${text(R.string.onboarding_weight)}").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(71.0, runBlocking { repos.settings.getProfile().weightKg }, 0.0)

        compose.onNodeWithText(text(R.string.settings_weight_journal_title)).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `planning - une puce change le jour et replanifie, réinitialisation confirmée`() {
        setScreen(healthSupported = false)

        // Lundi : Vélo → Cervicales (première des sept puces, libellé absent des autres cartes).
        compose.onAllNodesWithText("Cervicales")[0].performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("muscu", runBlocking { repos.settings.snapshot().weekPlan[0].kind })
        assertEquals(1, reminders.applied)

        compose.onNodeWithText(text(R.string.settings_plan_reset)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.settings_plan_reset_confirm)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals("velo", runBlocking { repos.settings.snapshot().weekPlan[0].kind })
    }

    @Test
    fun `données - effacement confirmé puis alerte`() {
        runBlocking { db.sessionDao().insert(TestSupport.session()) }
        setScreen(healthSupported = false)

        compose.onNodeWithText(text(R.string.settings_data_clear)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.settings_data_clear_confirm)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()

        assertTrue(runBlocking { repos.sessions.listSessions().isEmpty() })
        compose.onNodeWithText(text(R.string.settings_data_cleared_title)).assertIsDisplayed()
    }

    @Test
    fun `sauvegarde - commutateur et champs liés au port, boutons inactifs sans config`() {
        setScreen(healthSupported = false)

        compose.onNodeWithContentDescription(text(R.string.settings_backup_auto)).performScrollTo().assertIsOff().performClick()
        compose.waitForIdle()
        assertEquals(true, backup.state.value.enabled)
        compose.onNodeWithContentDescription(text(R.string.settings_backup_auto)).assertIsOn()

        compose.onNodeWithText(text(R.string.settings_backup_now)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.settings_backup_restore)).performScrollTo().performClick()
        // Config incomplète : aucun dialogue de confirmation.
        compose.onNodeWithText(text(R.string.settings_backup_restore_title)).assertDoesNotExist()
    }
}
