package ovh.battistella.elan.ui.screens

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.battistella.elan.domain.ActivityType
import ovh.battistella.elan.ui.screens.common.BleCadencePort
import ovh.battistella.elan.ui.screens.common.BleHeartRatePort
import ovh.battistella.elan.ui.screens.common.CadencePort
import ovh.battistella.elan.ui.screens.common.HeartRatePort
import ovh.battistella.elan.ui.screens.outing.OutingPort
import ovh.battistella.elan.ui.screens.outing.OutingUi
import ovh.battistella.elan.ui.screens.outing.TrackingOutingPort
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreensModule {
    @Binds
    @Singleton
    abstract fun bindOutingPort(impl: TrackingOutingPort): OutingPort

    @Binds
    @Singleton
    abstract fun bindHeartRatePort(impl: BleHeartRatePort): HeartRatePort

    @Binds
    @Singleton
    abstract fun bindCadencePort(impl: BleCadencePort): CadencePort
}

class NoOpOutingPort : OutingPort {
    private val _state = MutableStateFlow(OutingUi())
    override val state: StateFlow<OutingUi> = _state.asStateFlow()
    override fun begin(type: ActivityType) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun finish() = Unit
    override fun retrySave() = Unit
    override fun discard() = Unit
}

class NoOpHeartRatePort : HeartRatePort {
    override val bpm: StateFlow<Int?> = MutableStateFlow(null)
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
}

class NoOpCadencePort : CadencePort {
    override val cadenceRpm: StateFlow<Int?> = MutableStateFlow(null)
    override val speedKmh: StateFlow<Double?> = MutableStateFlow(null)
    override val hasSensor: StateFlow<Boolean> = MutableStateFlow(false)
}
