package ovh.battistella.elan.data.secrets

import java.util.concurrent.ConcurrentHashMap

/**
 * Stockage des secrets (identifiants S3) hors de la base : jamais dans
 * `settings`, donc jamais dans une sauvegarde ni un export. Remplace
 * expo-secure-store.
 */
interface SecretStore {
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String)
    suspend fun remove(name: String)

    companion object {
        /** Noms des secrets S3, identiques à ceux de l'app d'origine. */
        const val BACKUP_S3_ACCESS_KEY_ID = "backup_s3_accessKeyId"
        const val BACKUP_S3_SECRET_ACCESS_KEY = "backup_s3_secretAccessKey"
    }
}

/** Implémentation mémoire pour les tests (et les aperçus). */
class InMemorySecretStore : SecretStore {
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun get(name: String): String? = values[name]

    override suspend fun put(name: String, value: String) {
        values[name] = value
    }

    override suspend fun remove(name: String) {
        values.remove(name)
    }
}
