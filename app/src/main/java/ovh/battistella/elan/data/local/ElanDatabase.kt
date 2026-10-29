package ovh.battistella.elan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        TrackPointEntity::class,
        MuscuSetEntity::class,
        BodyMeasurementEntity::class,
        SettingEntity::class,
    ],
    version = ElanDatabase.VERSION,
    exportSchema = true,
)
abstract class ElanDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun muscuSetDao(): MuscuSetDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "elan.db"

        const val VERSION = 8
    }
}
