// Statut d'un gestionnaire de capteur BLE (miroir de `HrStatus` / `CscStatus`).
package ovh.battistella.elan.sensors.ble

enum class SensorStatus {
    /** Pas de radio BLE sur l'appareil. */
    Unsupported,
    Idle,
    Scanning,
    Connecting,
    Connected,
    /** Coupure involontaire : nouvelle tentative programmée (back-off). */
    Reconnecting,
    Error,
}
