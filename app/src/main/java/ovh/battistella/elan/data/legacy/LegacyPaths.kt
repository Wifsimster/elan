package ovh.battistella.elan.data.legacy

import android.content.Context
import java.io.File

class LegacyPaths(val filesDir: File, val sharedPrefsDir: File) {

    constructor(context: Context) : this(context.filesDir, File(context.dataDir, "shared_prefs"))

    val sqliteDir: File get() = File(filesDir, SQLITE_DIR)
    val dbFile: File get() = File(sqliteDir, DB_NAME)
    val walFile: File get() = File(sqliteDir, "$DB_NAME-wal")
    val shmFile: File get() = File(sqliteDir, "$DB_NAME-shm")
    val migratedDir: File get() = File(filesDir, MIGRATED_DIR)

    val hasLegacyDb: Boolean get() = dbFile.isFile

    val legacyBytes: Long get() = dbFile.length() + walFile.length()

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

        const val SECURE_STORE_PREFS = "SecureStore"
        const val SECURE_STORE_PREFS_FILE = "$SECURE_STORE_PREFS.xml"
        const val TASK_MANAGER_PREFS_FILE = "TaskManagerModule.xml"
        const val NOTIFICATIONS_PREFS_PREFIX = "expo.modules.notifications."
    }
}
