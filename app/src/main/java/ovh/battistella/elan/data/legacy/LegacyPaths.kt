package ovh.battistella.elan.data.legacy

import android.content.Context
import java.io.File

/**
 * Emplacements des données laissées par l'app d'origine (Expo) dans le bac à
 * sable de l'application — le paquet est le même, donc les fichiers sont
 * encore là après la mise à jour :
 *
 * - expo-sqlite : `files/SQLite/suivi-sport.db` (+ `-wal`, `-shm`), en WAL ;
 * - expo-secure-store : `shared_prefs/SecureStore.xml` (secrets S3 chiffrés) ;
 * - autres préférences Expo (gestionnaire de tâches, notifications).
 *
 * Après import, le dossier `SQLite` est renommé `SQLite.migrated` : hors de
 * portée d'un nouvel import, mais conservé un mois comme filet de sécurité.
 */
class LegacyPaths(val filesDir: File, val sharedPrefsDir: File) {

    constructor(context: Context) : this(context.filesDir, File(context.dataDir, "shared_prefs"))

    val sqliteDir: File get() = File(filesDir, SQLITE_DIR)
    val dbFile: File get() = File(sqliteDir, DB_NAME)
    val walFile: File get() = File(sqliteDir, "$DB_NAME-wal")
    val shmFile: File get() = File(sqliteDir, "$DB_NAME-shm")
    val migratedDir: File get() = File(filesDir, MIGRATED_DIR)

    /** Une base héritée est présente (fichier principal, même vide). */
    val hasLegacyDb: Boolean get() = dbFile.isFile

    /** Taille cumulée base + journal WAL (le `-shm` est un index, sans données). */
    val legacyBytes: Long get() = dbFile.length() + walFile.length()

    /** Préférences Expo à purger une fois la migration digérée. */
    fun legacyPrefFiles(): List<File> {
        val listed = sharedPrefsDir.listFiles()?.toList().orEmpty()
        return listed.filter { f ->
            f.name == SECURE_STORE_PREFS_FILE ||
                f.name == TASK_MANAGER_PREFS_FILE ||
                (f.name.startsWith(NOTIFICATIONS_PREFS_PREFIX) && f.name.endsWith(".xml"))
        }
    }

    companion object {
        const val SQLITE_DIR = "SQLite"
        const val MIGRATED_DIR = "SQLite.migrated"
        const val DB_NAME = "suivi-sport.db"

        /** Nom du `SharedPreferences` d'expo-secure-store. */
        const val SECURE_STORE_PREFS = "SecureStore"
        const val SECURE_STORE_PREFS_FILE = "$SECURE_STORE_PREFS.xml"
        const val TASK_MANAGER_PREFS_FILE = "TaskManagerModule.xml"
        const val NOTIFICATIONS_PREFS_PREFIX = "expo.modules.notifications."
    }
}
