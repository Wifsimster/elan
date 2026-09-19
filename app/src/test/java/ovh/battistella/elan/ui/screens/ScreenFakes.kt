package ovh.battistella.elan.ui.screens

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.battistella.elan.common.SnackbarController
import ovh.battistella.elan.data.local.ElanDatabase
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.testing.TestSupport
import ovh.battistella.elan.ui.screens.common.CadencePort
import ovh.battistella.elan.ui.screens.common.HeartRatePort
import ovh.battistella.elan.ui.screens.history.HistoryViewModel
import ovh.battistella.elan.ui.screens.home.HomeViewModel
import ovh.battistella.elan.ui.screens.outing.OutingPort
import ovh.battistella.elan.ui.screens.outing.OutingUi
import ovh.battistella.elan.ui.screens.outing.OutingViewModel
import ovh.battistella.elan.ui.screens.session.SessionDetailViewModel
import ovh.battistella.elan.ui.screens.session.SessionMapViewModel
import ovh.battistella.elan.ui.screens.weight.WeightViewModel
import ovh.battistella.elan.ui.screens.settings.BackupViewModel
import ovh.battistella.elan.ui.screens.settings.FakeBackupPort
import ovh.battistella.elan.ui.screens.settings.FakeExportPort
import ovh.battistella.elan.ui.screens.settings.FakeHealthConnectPort
import ovh.battistella.elan.ui.screens.settings.FakeMapStylePort
import ovh.battistella.elan.ui.screens.settings.FakeRemindersPort
import ovh.battistella.elan.ui.screens.settings.FakeStravaImportPort
import ovh.battistella.elan.ui.screens.settings.SensorsViewModel
import ovh.battistella.elan.ui.screens.settings.SettingsViewModel
import ovh.battistella.elan.sensors.ble.BleScanner
import ovh.battistella.elan.sensors.ble.CadenceSpeedManager
import ovh.battistella.elan.sensors.ble.FakeBleAdapterState
import ovh.battistella.elan.sensors.ble.FakeGattLinkFactory
import ovh.battistella.elan.sensors.ble.FakeLeScanner
import ovh.battistella.elan.sensors.ble.HeartRateManager
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.ui.screens.catalog.CatalogViewModel
import ovh.battistella.elan.ui.screens.exercise.ExerciseViewModel
import ovh.battistella.elan.ui.screens.progression.ProgressionViewModel
import ovh.battistella.elan.ui.screens.strength.StrengthViewModel
import ovh.battistella.elan.tracking.SessionFinalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.util.Optional

/** Suivi GPS piloté par le test : l'état est posé à la main, les appels sont journalisés. */
class FakeOutingPort(initial: OutingUi = OutingUi()) : OutingPort {
    val mutable = MutableStateFlow(initial)
    override val state: StateFlow<OutingUi> = mutable.asStateFlow()
    val calls = mutableListOf<String>()

    override fun begin(type: ActivityType) { calls += "begin:${type.key}" }
    override fun pause() { calls += "pause" }
    override fun resume() { calls += "resume" }
    override fun finish() { calls += "finish" }
    override fun retrySave() { calls += "retrySave" }
    override fun discard() { calls += "discard" }

    fun update(block: OutingUi.() -> OutingUi) { mutable.value = mutable.value.block() }
}

class FakeHeartRatePort(bpm: Int? = null, connected: Boolean = false) : HeartRatePort {
    override val bpm = MutableStateFlow(bpm)
    override val connected = MutableStateFlow(connected)
}

class FakeCadencePort(cadenceRpm: Int? = null, speedKmh: Double? = null, hasSensor: Boolean = false) : CadencePort {
    override val cadenceRpm = MutableStateFlow(cadenceRpm)
    override val speedKmh = MutableStateFlow(speedKmh)
    override val hasSensor = MutableStateFlow(hasSensor)
}

/**
 * Fabrique de ViewModels pour les tests de navigation : vrais dépôts sur une
 * base en mémoire, ports factices, horloge fournie. Le `SavedStateHandle`
 * vient des extras du `NavBackStackEntry` (arguments de route).
 */
class TestViewModelFactory(
    private val db: ElanDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val outing: FakeOutingPort = FakeOutingPort(),
    private val heart: FakeHeartRatePort = FakeHeartRatePort(),
    private val cadence: FakeCadencePort = FakeCadencePort(),
    private val snackbar: SnackbarController = SnackbarController(),
    private val export: FakeExportPort = FakeExportPort(),
    private val backup: FakeBackupPort = FakeBackupPort(),
) : ViewModelProvider.Factory {
    private val repos = TestSupport.repositories(db)
    private val sensors by lazy { testBleManagers(repos.settings, clock) }
    private val progression = TestSupport.progressionRunner(repos)
    private val finalizer = SessionFinalizer(Optional.empty(), Optional.empty(), CoroutineScope(Dispatchers.Unconfined))
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle: SavedStateHandle by lazy { extras.createSavedStateHandle() }
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(repos.sessions, repos.settings, heart, clock, snackbar, context, progression)
            HistoryViewModel::class.java -> HistoryViewModel(repos.sessions, clock)
            OutingViewModel::class.java -> OutingViewModel(handle, outing, cadence)
            SessionDetailViewModel::class.java -> SessionDetailViewModel(handle, repos.sessions, repos.settings, snackbar, context, finalizer, export)
            SessionMapViewModel::class.java -> SessionMapViewModel(handle, repos.sessions)
            WeightViewModel::class.java -> WeightViewModel(repos.bodyWeight, repos.settings, clock)
            StrengthViewModel::class.java -> StrengthViewModel(handle, repos.sessions, repos.settings, progression, finalizer, heart, clock, context)
            ProgressionViewModel::class.java -> ProgressionViewModel(repos.sessions, repos.settings, clock)
            ExerciseViewModel::class.java -> ExerciseViewModel(handle, repos.sessions)
            CatalogViewModel::class.java -> CatalogViewModel(repos.settings)
            SettingsViewModel::class.java -> SettingsViewModel(
                repos.settings, repos.sessions, FakeRemindersPort(), FakeHealthConnectPort(isSupported = false),
                FakeMapStylePort(), export, FakeStravaImportPort(), clock, context,
            )
            SensorsViewModel::class.java -> SensorsViewModel(sensors.first, sensors.second)
            BackupViewModel::class.java -> BackupViewModel(backup, repos.settings)
            else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
        } as T
    }
}

/**
 * Gestionnaires BLE sur radio factice (aucun appareil, adaptateur allumé) :
 * les cartes capteurs se rendent en état « Non connectée » sans Bluetooth.
 */
fun testBleManagers(
    settings: SettingsRepository,
    clock: Clock = Clock.systemUTC(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
): Pair<HeartRateManager, CadenceSpeedManager> {
    val scanner = BleScanner(FakeLeScanner(), scope)
    val links = FakeGattLinkFactory()
    val adapter = FakeBleAdapterState(enabled = true)
    val hr = HeartRateManager(scanner, links, adapter, { true }, settings, clock, scope)
    val csc = CadenceSpeedManager(scanner, links, adapter, { true }, settings, clock, scope)
    return hr to csc
}
