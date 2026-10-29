package ovh.battistella.elan.data.secrets

import java.util.concurrent.ConcurrentHashMap

interface SecretStore {
    suspend fun get(name: String): String?
    suspend fun put(name: String, value: String)
    suspend fun remove(name: String)

    companion object {
        const val BACKUP_S3_ACCESS_KEY_ID = "backup_s3_accessKeyId"
        const val BACKUP_S3_SECRET_ACCESS_KEY = "backup_s3_secretAccessKey"
    }
}

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
