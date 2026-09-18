package ovh.battistella.elan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base locale `elan.db`. Version 8 : le schéma v7 de l'app d'origine, repris
 * tel quel, +1 pour distinguer la base Room de l'ancienne `suivi-sport.db`.
 *
 * Les schémas sont exportés dans `app/schemas` et versionnés : c'est contre
 * eux que `MigrationTest` valide chaque migration, donc un changement de schéma
 * livré sans migration échoue dans les tests plutôt qu'en effaçant la base
 * d'un utilisateur au lancement (aucun repli destructif, comme Ondes).
 */
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

        /** Estampille aussi les sauvegardes (refus d'une restauration plus récente). */
        const val VERSION = 8
    }
}
