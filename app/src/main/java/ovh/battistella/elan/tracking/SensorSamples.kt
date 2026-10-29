package ovh.battistella.elan.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class HrFrame(val ts: Long, val bpm: Int)

data class CscFrame(val ts: Long, val cadenceRpm: Int?, val speedKmh: Double?)

interface HeartRateSamples {
    fun frames(): Flow<HrFrame>

    object None : HeartRateSamples {
        override fun frames(): Flow<HrFrame> = emptyFlow()
    }
}

interface CadenceSamples {
    fun frames(): Flow<CscFrame>

    object None : CadenceSamples {
        override fun frames(): Flow<CscFrame> = emptyFlow()
    }
}
