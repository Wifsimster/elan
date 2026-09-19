// Table de vérité des activités : ce que chacune est, comment on la nomme, ce
// qu'elle mesure. Sans dépendance au thème (les écrans résolvent `colorKey`)
// pour rester une brique pure et testable.
package ovh.battistella.elan.domain

data class ActivityMeta(
    val label: String,
    /** Libellé court, pour les puces de filtre et les boutons. */
    val shortLabel: String,
    /** Nom d'icône (Material Community Icons dans l'app d'origine). */
    val icon: String,
    /** Clé de teinte du thème. */
    val colorKey: String,
    /** L'activité produit un tracé GPS : distance, vitesse, dénivelé, carte. */
    val gps: Boolean,
    /**
     * L'allure (min/km) parle mieux que la vitesse (km/h) — vrai à pied, faux à
     * vélo. Ne change que l'affichage : la base stocke toujours des km/h.
     */
    val pace: Boolean,
)

val ACTIVITY_META: Map<ActivityType, ActivityMeta> = mapOf(
    ActivityType.VELO to ActivityMeta("Vélo", "Vélo", "bike", "velo", gps = true, pace = false),
    ActivityType.COURSE to ActivityMeta("Course à pied", "Course", "run", "course", gps = true, pace = true),
    ActivityType.MARCHE to ActivityMeta("Marche", "Marche", "walk", "marche", gps = true, pace = true),
    ActivityType.MUSCU to ActivityMeta("Musculation", "Muscu", "dumbbell", "muscu", gps = false, pace = false),
)

/** Types d'activité dans l'ordre d'affichage (accueil, filtres, objectifs). */
val ACTIVITY_TYPES: List<ActivityType> = listOf(
    ActivityType.VELO,
    ActivityType.COURSE,
    ActivityType.MARCHE,
    ActivityType.MUSCU,
)

val ActivityType.meta: ActivityMeta
    get() = ACTIVITY_META.getValue(this)

/** L'activité est-elle tracée au GPS ? Garde partout où il y a « un tracé ». */
fun isGpsActivity(type: ActivityType): Boolean = type.meta.gps

/** Faut-il présenter l'effort en allure (min/km) plutôt qu'en vitesse (km/h) ? */
fun usesPace(type: ActivityType): Boolean = type.meta.pace

/**
 * Convertit une valeur inconnue (paramètre de route, réglage restauré) en type
 * d'activité : seule une chaîne égale à une clé connue passe, tout le reste
 * retombe sur `fallback`.
 */
fun toActivityType(value: Any?, fallback: ActivityType = ActivityType.VELO): ActivityType =
    (value as? String)?.let { ActivityType.fromKey(it) } ?: fallback
