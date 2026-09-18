// Modèles des réglages persistés en JSON dans la table `settings`. Les défauts
// sont ceux de l'app d'origine (`src/lib/*.ts`, `src/hooks/*.tsx`).
package ovh.battistella.elan.data.settings

import ovh.battistella.elan.domain.ProgressionChange

/** Un capteur BLE mémorisé (ceinture cardio ou capteur CSC). */
data class BleDevice(val id: String, val name: String)

/** Région acceptée par MinIO/SeaweedFS/Garage sans configuration. */
const val DEFAULT_S3_REGION = "us-east-1"

/** Nom d'objet par défaut dans le bucket. */
const val DEFAULT_BACKUP_OBJECT_KEY = "elan-backup.json"

/**
 * Configuration de la sauvegarde S3 auto-hébergée, SANS les secrets
 * (`accessKeyId`/`secretAccessKey` vivent dans le [ovh.battistella.elan.data.secrets.SecretStore]).
 * Désactivée et vide par défaut : fonction opt-in « votre propre serveur »,
 * rien ne part sur le réseau tant que l'utilisateur n'a rien saisi.
 */
data class BackupConfig(
    /** Sauvegarde automatique après chaque séance. */
    val enabled: Boolean = false,
    /** URL de base du service, ex. https://minio.mon-homelab.tld. */
    val endpoint: String = "",
    val region: String = DEFAULT_S3_REGION,
    val bucket: String = "",
    val objectKey: String = DEFAULT_BACKUP_OBJECT_KEY,
)

/** Résultat de la dernière sauvegarde (succès ou échec). */
data class BackupLast(val at: Long, val ok: Boolean, val error: String? = null)

/** Rappels de séance quotidiens (heure locale 0..23). */
data class NotificationConfig(val enabled: Boolean = false, val hour: Int = 12)

/** Progression automatique du programme (activée par défaut). */
data class AutoProgressionConfig(val enabled: Boolean = true)

/**
 * État de la progression hebdomadaire : semaine ISO évaluée, ajustements
 * calculés, bannière fermée ou non. `week` vide = jamais évaluée.
 */
data class AutoProgressionState(
    val week: String = "",
    val changes: List<ProgressionChange> = emptyList(),
    val dismissed: Boolean = false,
)
