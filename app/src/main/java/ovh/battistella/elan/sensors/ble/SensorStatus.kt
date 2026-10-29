package ovh.battistella.elan.sensors.ble

enum class SensorStatus {
    Unsupported,
    Idle,
    Scanning,
    Connecting,
    Connected,
    Reconnecting,
    Error,
}
