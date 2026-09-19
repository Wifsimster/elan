package ovh.battistella.elan.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.R
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.data.settings.BackupConfig
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.domain.DEFAULT_WEEK_PLAN
import ovh.battistella.elan.domain.GoalActivity
import ovh.battistella.elan.domain.GoalMetric
import ovh.battistella.elan.domain.GoalPeriod
import ovh.battistella.elan.domain.PlannedSession
import ovh.battistella.elan.domain.Sex
import ovh.battistella.elan.domain.TemplateId
import ovh.battistella.elan.domain.TrainingGoal
import ovh.battistella.elan.testing.MainDispatcherRule
import ovh.battistella.elan.testing.TestSupport
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private lateinit var db: ElanDatabase
    private lateinit var repos: TestSupport.Repositories
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 1_800_000_000_000L
    private val reminders = FakeRemindersPort()
    private val health = FakeHealthConnectPort()
    private val map = FakeMapStylePort()
    private val export = FakeExportPort()
    private val strava = FakeStravaImportPort()

    @Before
    fun setUp() {
        db = TestSupport.inMemoryDb()
        repos = TestSupport.repositories(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** État courant après propagation des flux (combine sur le dispatcher de test). */
    private fun TestScope.ui(vm: SettingsViewModel): SettingsUi {
        advanceUntilIdle()
        return vm.ui.value
    }

    /** Collecte de `ui` (WhileSubscribed) : annulable pour libérer le flux Room le temps d'une transaction sur `settings`. */
    private var uiJob: Job? = null

    private fun TestScope.vm(): SettingsViewModel {
        val vm = SettingsViewModel(
            repos.settings, repos.sessions, reminders, health, map, export, strava,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()), context,
        )
        uiJob = backgroundScope.launch { vm.ui.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `profil - chaque champ est persisté immédiatement`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        vm.setWeight(82)
        vm.setHeight(180)
        vm.setMaxHr(185)
        vm.setTrainingGoal(TrainingGoal.FORCE)
        vm.setSex(Sex.F)
        advanceUntilIdle()

        val p = repos.settings.getProfile()
        assertEquals(82.0, p.weightKg, 0.0)
        assertEquals(180.0, p.heightCm, 0.0)
        assertEquals(185.0, p.maxHr, 0.0)
        assertEquals(TrainingGoal.FORCE, p.goal)
        assertEquals(Sex.F, p.sex)
        assertEquals(TrainingGoal.FORCE, ui(vm).profile.goal)
    }

    @Test
    fun `objectifs - cible bornée par la métrique, activité GPS seule pour la distance`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertEquals(3, ui(vm).goalForm.target)

        vm.setGoalTarget(99)
        assertEquals(30, ui(vm).goalForm.target)
        vm.setGoalTarget(0)
        assertEquals(1, ui(vm).goalForm.target)

        vm.setGoalActivity(GoalActivity.MUSCU)
        vm.setGoalMetric(GoalMetric.DISTANCE)
        // Défaut de la distance, et la muscu (sans distance) retombe sur « toutes ».
        assertEquals(100, ui(vm).goalForm.target)
        assertEquals(GoalActivity.ALL, ui(vm).goalForm.activity)
        vm.setGoalActivity(GoalActivity.MUSCU)
        assertEquals(GoalActivity.ALL, ui(vm).goalForm.activity)
        vm.setGoalActivity(GoalActivity.VELO)
        assertEquals(GoalActivity.VELO, ui(vm).goalForm.activity)
        vm.setGoalTarget(5000)
        assertEquals(1000, ui(vm).goalForm.target)

        vm.setGoalMetric(GoalMetric.TONNAGE)
        assertEquals(5000, ui(vm).goalForm.target)
        vm.setGoalTarget(50)
        assertEquals(100, ui(vm).goalForm.target)
    }

    @Test
    fun `objectifs - ajout puis suppression persistés`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        vm.setGoalMetric(GoalMetric.DISTANCE)
        vm.setGoalActivity(GoalActivity.VELO)
        vm.setGoalPeriod(GoalPeriod.MONTH)
        vm.setGoalTarget(150)
        vm.addGoal()
        advanceUntilIdle()

        val goal = ui(vm).goals.single()
        assertEquals(GoalMetric.DISTANCE, goal.metric)
        assertEquals(GoalActivity.VELO, goal.activity)
        assertEquals(GoalPeriod.MONTH, goal.period)
        assertEquals(150.0, goal.target, 0.0)
        assertEquals(1, repos.settings.snapshot().goals.size)

        // Le tonnage ignore l'activité choisie.
        vm.setGoalMetric(GoalMetric.TONNAGE)
        vm.addGoal()
        advanceUntilIdle()
        assertEquals(GoalActivity.ALL, ui(vm).goals[1].activity)

        vm.removeGoal(goal.id)
        advanceUntilIdle()
        assertEquals(1, ui(vm).goals.size)
        assertEquals(GoalMetric.TONNAGE, repos.settings.snapshot().goals.single().metric)
    }

    @Test
    fun `planning - changement d'un jour persisté et rappels replanifiés, réinitialisation`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        val option = WEEK_PLAN_OPTIONS.first { it.label == "Dos" }
        vm.setPlanDay(2, option.planned)
        advanceUntilIdle()

        val plan = ui(vm).weekPlan
        assertEquals(TemplateId.DOS_LOMBAIRE, (plan[2] as PlannedSession.Muscu).templateId)
        assertEquals(DEFAULT_WEEK_PLAN[0], plan[0])
        assertNotNull(repos.settings.snapshot().customWeekPlan)
        assertEquals(1, reminders.applied)

        vm.setPlanDay(0, WEEK_PLAN_OPTIONS.first { it.label == "Course" }.planned)
        advanceUntilIdle()
        assertEquals("course", ui(vm).weekPlan[0].kind)
        assertEquals(2, reminders.applied)

        vm.requestResetWeekPlan()
        assertEquals(SettingsDialog.ResetWeekPlan, ui(vm).dialog)
        vm.confirmResetWeekPlan()
        advanceUntilIdle()
        assertEquals(SettingsDialog.None, ui(vm).dialog)
        assertNull(repos.settings.snapshot().customWeekPlan)
        assertEquals(DEFAULT_WEEK_PLAN, ui(vm).weekPlan)
        assertEquals(3, reminders.applied)
    }

    @Test
    fun `options du planning - correspondance par kind ou template`() {
        val muscuA = WEEK_PLAN_OPTIONS.first { it.label == "Muscu A" }
        val velo = WEEK_PLAN_OPTIONS.first { it.label == "Vélo" }
        val rest = WEEK_PLAN_OPTIONS.first { it.label == "Repos" }
        assertTrue(isOptionActive(muscuA, DEFAULT_WEEK_PLAN[1]))
        assertFalse(isOptionActive(muscuA, DEFAULT_WEEK_PLAN[4]))
        assertTrue(isOptionActive(velo, DEFAULT_WEEK_PLAN[0]))
        assertTrue(isOptionActive(rest, PlannedSession.Repos))
        assertFalse(isOptionActive(rest, DEFAULT_WEEK_PLAN[0]))
        assertEquals("Vélo", (velo.planned as PlannedSession.Outing).label)
        assertEquals("Full-body A", (muscuA.planned as PlannedSession.Muscu).label)
    }

    @Test
    fun `rappels - activation replanifie, refus signalé, heure bornée`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        vm.setNotificationsEnabled(true)
        advanceUntilIdle()
        assertTrue(ui(vm).notifications.enabled)
        assertEquals(1, reminders.applied)

        vm.setNotificationHour(30)
        advanceUntilIdle()
        assertEquals(23, ui(vm).notifications.hour)
        assertEquals(2, reminders.applied)

        vm.setNotificationsEnabled(false)
        advanceUntilIdle()
        assertFalse(ui(vm).notifications.enabled)
        // Heure changée hors activation : pas de replanification.
        vm.setNotificationHour(8)
        advanceUntilIdle()
        assertEquals(8, repos.settings.snapshot().notifications.hour)
        assertEquals(3, reminders.applied)

        vm.notificationsDenied()
        assertTrue(ui(vm).notificationsError)
        vm.setNotificationsEnabled(true)
        assertFalse(ui(vm).notificationsError)
    }

    @Test
    fun `progression auto et zone de confidentialité persistées`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertTrue(ui(vm).autoProgression)
        vm.setAutoProgression(false)
        vm.setPrivacyZone(200)
        advanceUntilIdle()
        assertFalse(ui(vm).autoProgression)
        assertEquals(200, ui(vm).privacyZoneM)
        assertEquals("200", repos.settings.getSetting(SettingsRepository.Keys.PRIVACY_ZONE_M))
    }

    private suspend fun seedForDataCard() {
        db.sessionDao().insert(TestSupport.session(type = ActivityType.VELO, startedAt = now - 1000))
        db.bodyMeasurementDao().insert(TestSupport.bodyMeasurement())
        repos.settings.saveProfile(repos.settings.getProfile().copy(weightKg = 90.0))
        repos.settings.setBackupConfig(BackupConfig(enabled = true, endpoint = "https://s3.test", bucket = "elan"))
        repos.settings.setSetting(SettingsRepository.Keys.LEGACY_IMPORT_DONE, "1")
        repos.settings.setMapStyleUrl("https://tiles.example/style.json")
    }

    @Test
    fun `effacer les séances - confirmation, séances supprimées, réglages et poids conservés`() = runTest(mainDispatcher.dispatcher) {
        seedForDataCard()
        val vm = vm()

        vm.requestClearSessions()
        assertEquals(SettingsDialog.ClearSessions, ui(vm).dialog)
        vm.dismissDialog()
        assertEquals(SettingsDialog.None, ui(vm).dialog)
        assertEquals(1, repos.sessions.listSessions().size)

        vm.requestClearSessions()
        vm.confirmClearSessions()
        advanceUntilIdle()
        assertTrue(repos.sessions.listSessions().isEmpty())
        assertEquals(90.0, repos.settings.getProfile().weightKg, 0.0)
        assertEquals(1, repos.bodyWeight.listBodyMeasurements().size)
        assertEquals("https://tiles.example/style.json", repos.settings.snapshot().mapStyleUrl)
        val cleared = ui(vm).dialog as SettingsDialog.Info
        assertEquals(context.getString(R.string.settings_data_cleared_title), cleared.title)
    }

    @Test
    fun `tout réinitialiser - profil, poids et réglages effacés, clés de sauvegarde et de migration gardées`() = runTest(mainDispatcher.dispatcher) {
        seedForDataCard()
        // Le ViewModel n'est abonné qu'après la réinitialisation : avec des
        // exécuteurs Room synchrones, un flux `settings` collecté pendant la
        // transaction se rafraîchirait DANS cette transaction suspendue, ce que
        // Room refuse (artefact du harnais, sans équivalent sur l'appareil).
        val vm = SettingsViewModel(
            repos.settings, repos.sessions, reminders, health, map, export, strava,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault()), context,
        )
        vm.requestResetAll()
        vm.confirmResetAll()
        advanceUntilIdle()

        assertEquals(70.0, repos.settings.getProfile().weightKg, 0.0)
        assertTrue(repos.sessions.listSessions().isEmpty())
        assertTrue(repos.bodyWeight.listBodyMeasurements().isEmpty())
        val s = repos.settings.snapshot()
        assertEquals("https://s3.test", s.backupConfig.endpoint)
        assertEquals("1", repos.settings.getSetting(SettingsRepository.Keys.LEGACY_IMPORT_DONE))
        assertEquals("", s.mapStyleUrl)
        assertFalse(s.onboardingDone)

        uiJob = backgroundScope.launch { vm.ui.collect {} }
        // Même artefact : après cette transaction, la première requête du flux
        // `settings` n'arrive qu'à l'invalidation suivante ; une écriture anodine la provoque.
        vm.setPrivacyZone(0)
        assertEquals(context.getString(R.string.settings_data_reset_title), (ui(vm).dialog as SettingsDialog.Info).title)
    }

    @Test
    fun `carte - bascule OpenFreeMap, serveur perso, préfixe toléré, URL invalide refusée`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertFalse(ui(vm).mapEnabled)

        vm.setMapEnabled(true)
        advanceUntilIdle()
        assertTrue(ui(vm).mapEnabled)
        assertFalse(ui(vm).mapCustom)
        assertEquals(map.openFreeMapStyleUrl, ui(vm).mapStyleUrl)
        assertEquals("", ui(vm).mapUrlField)

        // Saisie progressive : « http » est un préfixe de https://, rien n'est persisté.
        vm.setCustomMapUrl("http")
        advanceUntilIdle()
        assertEquals("http", ui(vm).mapUrlField)
        assertEquals(map.openFreeMapStyleUrl, ui(vm).mapStyleUrl)

        vm.setCustomMapUrl("https://tiles.example/style.json")
        advanceUntilIdle()
        assertTrue(ui(vm).mapCustom)
        assertEquals("https://tiles.example/style.json", ui(vm).mapStyleUrl)
        assertEquals("https://tiles.example/style.json", ui(vm).mapUrlField)

        vm.setCustomMapUrl("http://clair.example")
        advanceUntilIdle()
        assertEquals(SettingsDialog.MapUrlInvalid, ui(vm).dialog)
        assertEquals("https://tiles.example/style.json", ui(vm).mapStyleUrl)
        vm.dismissDialog()

        // Champ vidé, carte active → retour au preset.
        vm.setCustomMapUrl("")
        advanceUntilIdle()
        assertEquals(map.openFreeMapStyleUrl, ui(vm).mapStyleUrl)

        vm.setMapEnabled(false)
        advanceUntilIdle()
        assertEquals("", ui(vm).mapStyleUrl)
        assertFalse(ui(vm).mapEnabled)
    }

    @Test
    fun `health connect - résultat des permissions et indisponibilité`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertTrue(ui(vm).healthSupported)
        assertEquals(health.permissions, vm.healthPermissions)

        vm.healthRequestStarted()
        assertTrue(ui(vm).healthBusy)
        vm.onHealthPermissionResult(health.permissions)
        advanceUntilIdle()
        assertTrue(ui(vm).healthEnabled)
        assertFalse(ui(vm).healthBusy)
        assertEquals(SettingsDialog.None, ui(vm).dialog)

        vm.disableHealthConnect()
        advanceUntilIdle()
        assertFalse(ui(vm).healthEnabled)

        health.outcome = HealthConnectOutcome.Denied
        vm.onHealthPermissionResult(emptySet())
        advanceUntilIdle()
        assertEquals(SettingsDialog.HealthDenied, ui(vm).dialog)
        vm.dismissDialog()

        vm.healthUnavailable()
        assertEquals(SettingsDialog.HealthUnavailable, ui(vm).dialog)
    }

    @Test
    fun `exports - fichier partagé avec le bon type MIME, erreur affichée`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        vm.exportMarkdown()
        advanceUntilIdle()
        vm.exportJson()
        advanceUntilIdle()
        assertEquals(listOf("markdown", "json"), export.calls)
        assertEquals(listOf("text/markdown", "application/json"), export.shared.map { it.second })
        assertNull(ui(vm).exporting)

        export.failure = IllegalStateException("Export impossible : disque plein.")
        vm.exportMarkdown()
        advanceUntilIdle()
        assertEquals("Export impossible : disque plein.", ui(vm).exportError)
        assertNull(ui(vm).exporting)
    }

    @Test
    fun `import Strava - bilan en dialogue et dernier import persisté hors sauvegarde`() = runTest(mainDispatcher.dispatcher) {
        val vm = vm()
        assertNull(ui(vm).stravaLast)

        vm.importStrava(listOf(Uri.parse("content://docs/a.gpx"), Uri.parse("content://docs/b.gpx")))
        advanceUntilIdle()

        assertEquals(2, strava.imported.single().size)
        val dialog = ui(vm).dialog as SettingsDialog.StravaResult
        assertEquals(2, dialog.report.imported)
        val last = ui(vm).stravaLast!!
        assertEquals(2, last.imported)
        assertEquals(1, last.duplicates)
        assertEquals(now, last.at)
        assertTrue(SettingsRepository.Keys.STRAVA_LAST_IMPORT in SettingsRepository.Keys.BACKUP_EXCLUDED)
        assertEquals(last, parseStravaLastImport(repos.settings.getSetting(SettingsRepository.Keys.STRAVA_LAST_IMPORT)))

        strava.failure = IllegalStateException("Fichier illisible.")
        vm.dismissDialog()
        vm.importStrava(listOf(Uri.parse("content://docs/c.fit")))
        advanceUntilIdle()
        assertEquals("Fichier illisible.", ui(vm).stravaError)
        assertFalse(ui(vm).stravaImporting)
    }
}


