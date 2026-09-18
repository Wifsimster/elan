package ovh.battistella.elan.ui.screens

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.ui.screens.common.CadencePort
import ovh.battistella.elan.ui.screens.common.HeartRatePort
import ovh.battistella.elan.ui.screens.outing.OutingPort
import ovh.battistella.elan.ui.screens.outing.OutingUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liaisons des ports consommés par les écrans. Les implémentations ci-dessous
 * sont INERTES : elles laissent l'interface compiler et se tester tant que le
 * suivi GPS (`tracking/`) et les capteurs BLE (`sensors/ble/`) ne sont pas
 * branchés. À remplacer par les adaptateurs réels sans toucher aux écrans.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreensModule {
    @Binds
    @Singleton
    abstract fun bindOutingPort(impl: NoOpOutingPort): OutingPort

    @Binds
    @Singleton
    abstract fun bindHeartRatePort(impl: NoOpHeartRatePort): HeartRatePort

    @Binds
    @Singleton
    abstract fun bindCadencePort(impl: NoOpCadencePort): CadencePort
}

/** Suivi GPS inerte : reste en `Idle`, aucune action n'a d'effet. */
@Singleton
class NoOpOutingPort @Inject constructor() : OutingPort {
    private val _state = MutableStateFlow(OutingUi())
    override val state: StateFlow<OutingUi> = _state.asStateFlow()
    override fun begin(type: ActivityType) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun finish() = Unit
    override fun retrySave() = Unit
    override fun discard() = Unit
}

/** Ceinture jamais connectée. */
@Singleton
class NoOpHeartRatePort @Inject constructor() : HeartRatePort {
    override val bpm: StateFlow<Int?> = MutableStateFlow(null)
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
}

/** Aucun capteur vélo. */
@Singleton
class NoOpCadencePort @Inject constructor() : CadencePort {
    override val cadenceRpm: StateFlow<Int?> = MutableStateFlow(null)
    override val speedKmh: StateFlow<Double?> = MutableStateFlow(null)
    override val hasSensor: StateFlow<Boolean> = MutableStateFlow(false)
}
