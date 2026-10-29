package ovh.battistella.elan.domain

data class ActivityMeta(
    val label: String,
    val shortLabel: String,
    val icon: String,
    val colorKey: String,
    val gps: Boolean,
    val pace: Boolean,
)

val ACTIVITY_META: Map<ActivityType, ActivityMeta> = mapOf(
    ActivityType.VELO to ActivityMeta("Vélo", "Vélo", "bike", "velo", gps = true, pace = false),
    ActivityType.COURSE to ActivityMeta("Course à pied", "Course", "run", "course", gps = true, pace = true),
    ActivityType.MARCHE to ActivityMeta("Marche", "Marche", "walk", "marche", gps = true, pace = true),
    ActivityType.MUSCU to ActivityMeta("Musculation", "Muscu", "dumbbell", "muscu", gps = false, pace = false),
)

val ACTIVITY_TYPES: List<ActivityType> = listOf(
    ActivityType.VELO,
    ActivityType.COURSE,
    ActivityType.MARCHE,
    ActivityType.MUSCU,
)

val ActivityType.meta: ActivityMeta
    get() = ACTIVITY_META.getValue(this)

fun isGpsActivity(type: ActivityType): Boolean = type.meta.gps

fun usesPace(type: ActivityType): Boolean = type.meta.pace

fun toActivityType(value: Any?, fallback: ActivityType = ActivityType.VELO): ActivityType =
    (value as? String)?.let { ActivityType.fromKey(it) } ?: fallback
