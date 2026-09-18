// État du suivi GPS d'une sortie (miroir de `GpsStatus` dans use-gps-tracker.ts).
package ovh.battistella.elan.domain

enum class GpsStatus {
    /** Aucun suivi demandé. */
    IDLE,
    /** Permission demandée ou premier fix en attente. */
    REQUESTING,
    /** Localisation refusée par l'utilisateur. */
    DENIED,
    /** Positions reçues. */
    TRACKING,
}
