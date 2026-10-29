package ovh.battistella.elan.domain

data class WheelSize(
    val label: String,
    val mm: Int,
)

val WHEEL_SIZES: List<WheelSize> = listOf(
    WheelSize("700×23c", 2096),
    WheelSize("700×25c", 2105),
    WheelSize("700×28c", 2136),
    WheelSize("700×32c", 2155),
    WheelSize("700×38c", 2180),
    WheelSize("650b", 2079),
    WheelSize("26×1.5", 1985),
    WheelSize("26×1.95", 2050),
    WheelSize("27.5×2.1", 2148),
    WheelSize("29×2.1", 2288),
    WheelSize("29×2.25", 2326),
)

fun matchWheelSize(mm: Int): WheelSize? = WHEEL_SIZES.firstOrNull { it.mm == mm }
