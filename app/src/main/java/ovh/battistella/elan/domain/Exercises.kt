// Catalogue d'exercices (« magasin ») + moteur de recommandation.
//
// Objectif : rendre la musculation libre et adaptée à tous. Plutôt qu'un
// programme figé, l'utilisateur pioche dans une bibliothèque d'exercices, en
// voit le détail (muscles, matériel, exécution) et reçoit un nombre de
// répétitions et une charge conseillés, calculés à partir de son profil (poids,
// taille, sexe) et de ce qu'il cherche à faire (objectif). Tout est ensuite
// personnalisable à la main dans la séance.
//
// 100 % local, sans dépendance UI : ce module ne contient que des données et de
// l'arithmétique, comme `Program.kt`. Les charges conseillées sont un POINT DE
// DÉPART pour un pratiquant intermédiaire ; le moteur de progression (dernière
// charge enregistrée) prend ensuite le relais.
package ovh.battistella.elan.domain

import kotlin.math.max
import kotlin.math.roundToLong

/** Grand groupe musculaire — sert de rayon dans le magasin (filtre/section). */
enum class ExerciseCategory(val label: String) {
    JAMBES("Jambes"),
    PECTORAUX("Pectoraux"),
    DOS("Dos"),
    EPAULES("Épaules"),
    BRAS("Bras"),
    GAINAGE("Gainage");

    companion object {
        fun fromLabel(label: String?): ExerciseCategory? = entries.firstOrNull { it.label == label }
    }
}

/** Matériel nécessaire — sert de second filtre (« je n'ai que des haltères »). */
enum class Equipment(val label: String) {
    POIDS_DU_CORPS("poids du corps"),
    HALTERES("haltères"),
    BARRE("barre"),
    KETTLEBELL("kettlebell"),
    ELASTIQUE("élastique"),
    MACHINE("machine");

    companion object {
        fun fromLabel(label: String?): Equipment? = entries.firstOrNull { it.label == label }
    }
}

/** Une fiche d'exercice du catalogue. */
data class CatalogExercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    /** Muscles sollicités (affichés en pastilles), libellés alignés sur `Program.kt`. */
    val muscles: List<String>,
    val equipment: List<Equipment>,
    /** Glyphe MaterialCommunityIcons illustrant le mouvement. */
    val icon: String,
    /** Clé d'illustration photo (paire départ → fin). */
    val imageKey: String? = null,
    /** « bras » / « jambe » / « côté » — travail unilatéral. */
    val perSideLabel: String? = null,
    /** Exercice chronométré (gainage) : les « reps » sont des secondes. */
    val timed: Boolean = false,
    /** Polyarticulaire (gros mouvement) vs isolation. */
    val compound: Boolean,
    /**
     * Base de la charge conseillée : charge de travail d'un pratiquant
     * intermédiaire en hypertrophie, exprimée en fraction du poids de corps.
     * 0 = au poids du corps / non chargeable.
     */
    val loadFactor: Double,
    /** Vrai si `loadFactor` s'entend PAR haltère (mouvements aux haltères). */
    val loadPerSide: Boolean = false,
)

/** Ordre d'affichage des rayons. */
val CATEGORIES: List<ExerciseCategory> = listOf(
    ExerciseCategory.JAMBES,
    ExerciseCategory.PECTORAUX,
    ExerciseCategory.DOS,
    ExerciseCategory.EPAULES,
    ExerciseCategory.BRAS,
    ExerciseCategory.GAINAGE,
)

/** Matériels filtrables (ordre d'affichage). */
val EQUIPMENTS: List<Equipment> = listOf(
    Equipment.POIDS_DU_CORPS,
    Equipment.HALTERES,
    Equipment.BARRE,
    Equipment.KETTLEBELL,
    Equipment.ELASTIQUE,
    Equipment.MACHINE,
)

val CATALOG: List<CatalogExercise> = listOf(
    // ---------- JAMBES ----------
    CatalogExercise(
        id = "goblet-squat",
        name = "Goblet squat",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Adducteurs", "Gainage"),
        equipment = listOf(Equipment.HALTERES, Equipment.KETTLEBELL),
        icon = "weight-kilogram",
        imageKey = "goblet-squat",
        compound = true,
        loadFactor = 0.3,
    ),
    CatalogExercise(
        id = "back-squat",
        name = "Squat barre",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Adducteurs", "Lombaires", "Gainage"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight-lifter",
        compound = true,
        loadFactor = 1.0,
    ),
    CatalogExercise(
        id = "romanian-deadlift",
        name = "Soulevé de terre roumain haltères",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Ischio-jambiers", "Fessiers", "Lombaires"),
        equipment = listOf(Equipment.HALTERES),
        icon = "dumbbell",
        imageKey = "dumbbell-romanian-deadlift",
        compound = true,
        loadFactor = 0.25,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "barbell-rdl",
        name = "Soulevé de terre roumain barre",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Ischio-jambiers", "Fessiers", "Lombaires", "Grand dorsal"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight",
        compound = true,
        loadFactor = 1.0,
    ),
    CatalogExercise(
        id = "dumbbell-lunges",
        name = "Fentes avant haltères",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Ischio-jambiers"),
        equipment = listOf(Equipment.HALTERES),
        icon = "walk",
        imageKey = "dumbbell-lunges",
        perSideLabel = "jambe",
        compound = true,
        loadFactor = 0.15,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "bulgarian-split-squat",
        name = "Fentes bulgares haltères",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Ischio-jambiers"),
        equipment = listOf(Equipment.HALTERES),
        icon = "run",
        imageKey = "bulgarian-split-squat",
        perSideLabel = "jambe",
        compound = true,
        loadFactor = 0.15,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "hip-thrust",
        name = "Hip thrust barre",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Fessiers", "Ischio-jambiers", "Quadriceps"),
        equipment = listOf(Equipment.BARRE),
        icon = "bridge",
        compound = true,
        loadFactor = 1.3,
    ),
    CatalogExercise(
        id = "glute-bridge",
        name = "Pont fessier",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Fessiers", "Lombaires", "Ischio-jambiers"),
        equipment = listOf(Equipment.POIDS_DU_CORPS, Equipment.HALTERES, Equipment.ELASTIQUE),
        icon = "gymnastics",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "leg-press",
        name = "Presse à cuisses",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Adducteurs"),
        equipment = listOf(Equipment.MACHINE),
        icon = "seat",
        compound = true,
        loadFactor = 1.8,
    ),
    CatalogExercise(
        id = "kettlebell-swing",
        name = "Kettlebell swing",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Fessiers", "Ischio-jambiers", "Lombaires", "Gainage"),
        equipment = listOf(Equipment.KETTLEBELL),
        icon = "kettlebell",
        compound = true,
        loadFactor = 0.22,
    ),
    CatalogExercise(
        id = "bodyweight-squat",
        name = "Squat au poids du corps",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Quadriceps", "Fessiers", "Adducteurs"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "human",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "standing-calf-raise",
        name = "Mollets debout",
        category = ExerciseCategory.JAMBES,
        muscles = listOf("Mollets"),
        equipment = listOf(Equipment.POIDS_DU_CORPS, Equipment.HALTERES, Equipment.MACHINE),
        icon = "foot-print",
        compound = false,
        loadFactor = 0.15,
        loadPerSide = true,
    ),

    // ---------- PECTORAUX ----------
    CatalogExercise(
        id = "dumbbell-bench-press",
        name = "Développé couché haltères",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Triceps", "Deltoïdes antérieurs"),
        equipment = listOf(Equipment.HALTERES),
        icon = "dumbbell",
        imageKey = "dumbbell-bench-press",
        compound = true,
        loadFactor = 0.22,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "barbell-bench-press",
        name = "Développé couché barre",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Triceps", "Deltoïdes antérieurs"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight-lifter",
        compound = true,
        loadFactor = 0.65,
    ),
    CatalogExercise(
        id = "incline-dumbbell-press",
        name = "Développé incliné haltères",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Deltoïdes antérieurs", "Triceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "stairs-up",
        compound = true,
        loadFactor = 0.18,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "push-up",
        name = "Pompes",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Triceps", "Deltoïdes antérieurs", "Gainage"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "human",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "dumbbell-fly",
        name = "Écarté couché haltères",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Deltoïdes antérieurs"),
        equipment = listOf(Equipment.HALTERES),
        icon = "arrow-collapse-horizontal",
        compound = false,
        loadFactor = 0.1,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "band-chest-press",
        name = "Développé poitrine élastique",
        category = ExerciseCategory.PECTORAUX,
        muscles = listOf("Pectoraux", "Triceps", "Deltoïdes antérieurs"),
        equipment = listOf(Equipment.ELASTIQUE),
        icon = "arrow-expand-horizontal",
        compound = true,
        loadFactor = 0.0,
    ),

    // ---------- DOS ----------
    CatalogExercise(
        id = "one-arm-dumbbell-row",
        name = "Rowing haltère un bras",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Trapèzes", "Rhomboïdes", "Biceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "dumbbell",
        imageKey = "one-arm-dumbbell-row",
        perSideLabel = "bras",
        compound = true,
        loadFactor = 0.28,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "bent-over-two-dumbbell-row",
        name = "Rowing penché 2 bras haltères",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Trapèzes", "Rhomboïdes", "Biceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "arm-flex",
        imageKey = "bent-over-two-dumbbell-row",
        compound = true,
        loadFactor = 0.22,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "barbell-row",
        name = "Rowing barre",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Trapèzes", "Rhomboïdes", "Biceps", "Lombaires"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight-lifter",
        compound = true,
        loadFactor = 0.6,
    ),
    CatalogExercise(
        id = "deadlift",
        name = "Soulevé de terre barre",
        category = ExerciseCategory.DOS,
        muscles = listOf("Lombaires", "Fessiers", "Ischio-jambiers", "Trapèzes", "Grand dorsal"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight",
        compound = true,
        loadFactor = 1.4,
    ),
    CatalogExercise(
        id = "pull-up",
        name = "Tractions",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Biceps", "Rhomboïdes", "Trapèzes"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "arm-flex-outline",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "lat-pulldown",
        name = "Tirage vertical poulie",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Biceps", "Rhomboïdes"),
        equipment = listOf(Equipment.MACHINE),
        icon = "chevron-double-down",
        compound = true,
        loadFactor = 0.6,
    ),
    CatalogExercise(
        id = "band-row",
        name = "Tirage horizontal élastique",
        category = ExerciseCategory.DOS,
        muscles = listOf("Grand dorsal", "Rhomboïdes", "Trapèzes", "Biceps"),
        equipment = listOf(Equipment.ELASTIQUE),
        icon = "arrow-collapse-horizontal",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "scapular-retraction",
        name = "Rétractions scapulaires",
        category = ExerciseCategory.DOS,
        muscles = listOf("Rhomboïdes", "Trapèzes", "Deltoïdes"),
        equipment = listOf(Equipment.POIDS_DU_CORPS, Equipment.ELASTIQUE),
        icon = "arrow-collapse-vertical",
        compound = false,
        loadFactor = 0.0,
    ),

    // ---------- ÉPAULES ----------
    CatalogExercise(
        id = "standing-dumbbell-press",
        name = "Développé épaules debout haltères",
        category = ExerciseCategory.EPAULES,
        muscles = listOf("Deltoïdes", "Triceps", "Gainage"),
        equipment = listOf(Equipment.HALTERES),
        icon = "dumbbell",
        imageKey = "standing-dumbbell-press",
        compound = true,
        loadFactor = 0.14,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "overhead-press-barbell",
        name = "Développé militaire barre",
        category = ExerciseCategory.EPAULES,
        muscles = listOf("Deltoïdes", "Triceps", "Trapèzes", "Gainage"),
        equipment = listOf(Equipment.BARRE),
        icon = "weight-lifter",
        compound = true,
        loadFactor = 0.45,
    ),
    CatalogExercise(
        id = "lateral-raise",
        name = "Élévations latérales",
        category = ExerciseCategory.EPAULES,
        muscles = listOf("Deltoïdes"),
        equipment = listOf(Equipment.HALTERES, Equipment.ELASTIQUE),
        icon = "arrow-expand-horizontal",
        compound = false,
        loadFactor = 0.06,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "rear-delt-fly",
        name = "Oiseau (deltoïdes postérieurs)",
        category = ExerciseCategory.EPAULES,
        muscles = listOf("Deltoïdes", "Rhomboïdes", "Trapèzes"),
        equipment = listOf(Equipment.HALTERES, Equipment.ELASTIQUE),
        icon = "arrow-collapse-vertical",
        compound = false,
        loadFactor = 0.05,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "band-face-pull",
        name = "Face pull élastique",
        category = ExerciseCategory.EPAULES,
        muscles = listOf("Deltoïdes", "Trapèzes", "Rhomboïdes"),
        equipment = listOf(Equipment.ELASTIQUE),
        icon = "arrow-collapse-horizontal",
        compound = false,
        loadFactor = 0.0,
    ),

    // ---------- BRAS ----------
    CatalogExercise(
        id = "dumbbell-biceps-curl",
        name = "Curl biceps haltères",
        category = ExerciseCategory.BRAS,
        muscles = listOf("Biceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "arm-flex",
        compound = false,
        loadFactor = 0.13,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "hammer-curl",
        name = "Curl marteau haltères",
        category = ExerciseCategory.BRAS,
        muscles = listOf("Biceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "dumbbell",
        compound = false,
        loadFactor = 0.14,
        loadPerSide = true,
    ),
    CatalogExercise(
        id = "triceps-overhead-extension",
        name = "Extension triceps nuque haltère",
        category = ExerciseCategory.BRAS,
        muscles = listOf("Triceps"),
        equipment = listOf(Equipment.HALTERES),
        icon = "arrow-up-bold",
        compound = false,
        loadFactor = 0.18,
    ),
    CatalogExercise(
        id = "triceps-dips-bench",
        name = "Dips entre deux appuis",
        category = ExerciseCategory.BRAS,
        muscles = listOf("Triceps", "Pectoraux", "Deltoïdes antérieurs"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "human",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "band-biceps-curl",
        name = "Curl biceps élastique",
        category = ExerciseCategory.BRAS,
        muscles = listOf("Biceps"),
        equipment = listOf(Equipment.ELASTIQUE),
        icon = "arm-flex-outline",
        compound = false,
        loadFactor = 0.0,
    ),

    // ---------- GAINAGE ----------
    CatalogExercise(
        id = "plank",
        name = "Gainage planche",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Abdominaux", "Transverse", "Lombaires"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "yoga",
        imageKey = "plank",
        timed = true,
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "side-plank",
        name = "Gainage latéral",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Obliques", "Transverse", "Moyen fessier"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "meditation",
        imageKey = "side-plank",
        perSideLabel = "côté",
        timed = true,
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "dead-bug",
        name = "Dead bug",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Transverse", "Abdominaux", "Gainage"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "bug",
        perSideLabel = "côté",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "bird-dog",
        name = "Bird-dog",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Gainage", "Transverse", "Lombaires", "Fessiers"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "dog",
        perSideLabel = "côté",
        compound = true,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "mcgill-curl-up",
        name = "McGill curl-up",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Abdominaux", "Transverse", "Gainage"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "gymnastics",
        compound = false,
        loadFactor = 0.0,
    ),
    CatalogExercise(
        id = "superman",
        name = "Superman (extension lombaire)",
        category = ExerciseCategory.GAINAGE,
        muscles = listOf("Lombaires", "Fessiers", "Dos"),
        equipment = listOf(Equipment.POIDS_DU_CORPS),
        icon = "arrow-up-bold",
        compound = false,
        loadFactor = 0.0,
    ),
)

// ---------------------------------------------------------------------------
// Objectifs d'entraînement
// ---------------------------------------------------------------------------

/** Paramètres d'un objectif : schéma de séries/reps/repos + intensité (charge). */
data class GoalSpec(
    val id: TrainingGoal,
    val label: String,
    /** Phrase courte décrivant l'objectif (affichée à la sélection). */
    val blurb: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSec: Int,
    /** Multiplie `loadFactor` pour obtenir la charge conseillée. */
    val intensityFactor: Double,
    /** Fourchette en secondes pour les exercices chronométrés (gainage). */
    val timedMinSec: Int,
    val timedMaxSec: Int,
)

// Fourchettes standard (NSCA / ACSM) : force = lourd/peu de reps/repos long,
// hypertrophie = modéré, endurance = léger/beaucoup de reps/repos court.
val GOALS: List<GoalSpec> = listOf(
    GoalSpec(
        id = TrainingGoal.FORCE,
        label = "Force",
        blurb = "Soulever lourd, peu de répétitions, longs repos.",
        sets = 5,
        repsMin = 3,
        repsMax = 6,
        restSec = 180,
        intensityFactor = 1.15,
        timedMinSec = 30,
        timedMaxSec = 60,
    ),
    GoalSpec(
        id = TrainingGoal.HYPERTROPHIE,
        label = "Prise de muscle",
        blurb = "Charges modérées, volume, repos moyens.",
        sets = 4,
        repsMin = 8,
        repsMax = 12,
        restSec = 90,
        intensityFactor = 1.0,
        timedMinSec = 30,
        timedMaxSec = 45,
    ),
    GoalSpec(
        id = TrainingGoal.ENDURANCE,
        label = "Endurance",
        blurb = "Charges légères, beaucoup de répétitions, repos courts.",
        sets = 3,
        repsMin = 15,
        repsMax = 20,
        restSec = 45,
        intensityFactor = 0.65,
        timedMinSec = 45,
        timedMaxSec = 90,
    ),
    GoalSpec(
        id = TrainingGoal.TONIFICATION,
        label = "Remise en forme",
        blurb = "Tonifier en douceur, charges modérées, polyvalent.",
        sets = 3,
        repsMin = 10,
        repsMax = 15,
        restSec = 60,
        intensityFactor = 0.8,
        timedMinSec = 25,
        timedMaxSec = 45,
    ),
    GoalSpec(
        id = TrainingGoal.PERTE_POIDS,
        label = "Perte de poids",
        blurb = "Rythme soutenu, repos courts, dépense énergétique.",
        sets = 3,
        repsMin = 12,
        repsMax = 15,
        restSec = 45,
        intensityFactor = 0.75,
        timedMinSec = 30,
        timedMaxSec = 60,
    ),
)

/** Spécification d'un objectif (repli sur l'hypertrophie si inconnu). */
fun goalSpec(goal: TrainingGoal?): GoalSpec = GOALS.firstOrNull { it.id == goal } ?: GOALS[1]

/** Libellé court d'un objectif, pour l'affichage. */
fun goalLabel(goal: TrainingGoal?): String = goalSpec(goal).label

// ---------------------------------------------------------------------------
// Moteur de recommandation
// ---------------------------------------------------------------------------

/** Facteurs haut / bas du corps appliqués à la charge selon le sexe. */
data class SexFactor(val upper: Double, val lower: Double)

// Femmes : charges absolues plus basses, écart plus marqué sur le haut du corps.
val SEX_FACTOR: Map<Sex?, SexFactor> = mapOf(
    Sex.F to SexFactor(upper = 0.65, lower = 0.8),
    Sex.H to SexFactor(upper = 1.0, lower = 1.0),
    null to SexFactor(upper = 1.0, lower = 1.0),
)

/** Catégories considérées « haut du corps » pour le facteur sexe. */
private val UPPER_BODY: Set<ExerciseCategory> = setOf(
    ExerciseCategory.PECTORAUX,
    ExerciseCategory.DOS,
    ExerciseCategory.EPAULES,
    ExerciseCategory.BRAS,
)

/**
 * Arrondit une charge à un pas réaliste : barre / charges lourdes au pas de
 * 2,5 kg, travail léger à l'haltère au kg, très léger au demi-kilo.
 */
fun roundWeight(kg: Double, ex: CatalogExercise): Double {
    if (kg <= 0.0) return 0.0
    val step = if (!ex.loadPerSide && ex.loadFactor >= 0.5) 2.5 else if (kg < 6) 0.5 else 1.0
    return max(0.0, (kg / step).roundToLong() * step)
}

/** Recommandation calculée pour un exercice donné et un profil donné. */
data class Recommendation(
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSec: Int,
    /** Charge conseillée en kg. Si `perDumbbell`, c'est la charge PAR haltère. */
    val weightKg: Double,
    /** Vrai ⇒ `weightKg` s'entend par haltère (afficher « / main »). */
    val perDumbbell: Boolean,
    /** Vrai ⇒ `repsMin`/`repsMax` sont des SECONDES (gainage chronométré). */
    val timed: Boolean,
)

/** Sous-ensemble de profil dont dépend la recommandation. */
data class RecoProfile(
    val weightKg: Double,
    val heightCm: Double,
    val sex: Sex?,
    val goal: TrainingGoal,
)

/** Projection d'un profil complet sur les champs utiles à la recommandation. */
fun Profile.toRecoProfile(): RecoProfile = RecoProfile(weightKg, heightCm, sex, goal)

/**
 * Calcule reps, séries, repos et charge conseillés pour un exercice à partir du
 * profil (poids, taille, sexe) et de l'objectif. La taille n'entre pas dans le
 * calcul de charge : le poids de corps capture déjà l'essentiel du gabarit, et
 * des bras de levier plus longs auraient un effet inverse non fiable — on évite
 * de compter deux fois la morphologie.
 */
fun recommend(profile: RecoProfile, ex: CatalogExercise): Recommendation {
    val goal = goalSpec(profile.goal)

    // 1) Exercices chronométrés (gainage) → pas de charge, reps = secondes.
    if (ex.timed) {
        return Recommendation(
            sets = goal.sets,
            repsMin = goal.timedMinSec,
            repsMax = goal.timedMaxSec,
            restSec = goal.restSec,
            weightKg = 0.0,
            perDumbbell = false,
            timed = true,
        )
    }

    // 2) Mouvements au poids du corps / non chargeables.
    if (ex.loadFactor == 0.0) {
        return Recommendation(
            sets = goal.sets,
            repsMin = goal.repsMin,
            repsMax = goal.repsMax,
            restSec = goal.restSec,
            weightKg = 0.0,
            perDumbbell = false,
            timed = false,
        )
    }

    // 3) Charge de base = loadFactor × poids de corps × intensité de l'objectif.
    var w = ex.loadFactor * profile.weightKg * goal.intensityFactor

    // 4) Facteur sexe (haut vs bas du corps).
    val factor = SEX_FACTOR.getValue(profile.sex)
    w *= if (ex.category in UPPER_BODY) factor.upper else factor.lower

    return Recommendation(
        sets = goal.sets,
        repsMin = goal.repsMin,
        repsMax = goal.repsMax,
        restSec = goal.restSec,
        weightKg = roundWeight(w, ex),
        perDumbbell = ex.loadPerSide,
        timed = false,
    )
}

/** Variante prenant le profil complet. */
fun recommend(profile: Profile, ex: CatalogExercise): Recommendation = recommend(profile.toRecoProfile(), ex)

// ---------------------------------------------------------------------------
// Recherche / formatage
// ---------------------------------------------------------------------------

/** Retrouve une fiche du catalogue par son id. */
fun catalogById(id: String?): CatalogExercise? {
    if (id.isNullOrEmpty()) return null
    return CATALOG.firstOrNull { it.id == id }
}

/** Retrouve une fiche par son nom exact (enrichit un exercice enregistré). */
fun catalogByName(name: String?): CatalogExercise? {
    if (name.isNullOrEmpty()) return null
    return CATALOG.firstOrNull { it.name == name }
}

/**
 * Indice de cible lisible à partir d'une recommandation, ex.
 * « 4 × 8-12 » ou « 3 × 30-45 s / côté ».
 */
fun recoHint(ex: CatalogExercise, rec: Recommendation): String {
    val reps = if (rec.repsMin == rec.repsMax) "${rec.repsMin}" else "${rec.repsMin}-${rec.repsMax}"
    val unit = if (rec.timed) " s" else ""
    val side = if (ex.perSideLabel != null) " / ${ex.perSideLabel}" else ""
    return "${rec.sets} × $reps$unit$side"
}

/** Libellé de charge conseillée, ex. « 16 kg / main » ou « au poids du corps ». */
fun recoWeightLabel(rec: Recommendation): String {
    if (rec.timed) return "gainage chronométré"
    if (rec.weightKg <= 0.0) return "au poids du corps"
    return "${fmtKg(rec.weightKg)} kg${if (rec.perDumbbell) " / main" else ""}"
}

// ---------------------------------------------------------------------------
// Exécution (« comment faire ») par exercice
// ---------------------------------------------------------------------------

// Texte d'exécution simple et sûr pour chaque mouvement. Repris à l'identique de
// `Program.kt` pour les exercices communs, rédigé dans le même esprit pour les
// autres. Séparé du tableau `CATALOG` pour garder ce dernier lisible.
private val HOWTO: Map<String, String> = mapOf(
    "goblet-squat" to
        "Tiens un haltère verticalement contre la poitrine, à deux mains. Pieds largeur d’épaules. Descends en pliant les genoux, dos droit et talons au sol, jusqu’à ce que les cuisses soient parallèles au sol, puis remonte.",
    "back-squat" to
        "Barre posée sur le haut du dos (trapèzes), pieds largeur d’épaules. Descends en envoyant les hanches vers l’arrière, dos gainé et talons au sol, jusqu’aux cuisses parallèles, puis remonte en poussant dans le sol. Commence léger, la technique prime.",
    "romanian-deadlift" to
        "Debout, haltères devant les cuisses, jambes quasi tendues (genoux légèrement fléchis). Penche le buste en envoyant les fesses vers l’arrière, dos bien droit, les haltères descendent le long des jambes jusqu’à sentir l’étirement des ischios, puis reviens en serrant les fessiers.",
    "barbell-rdl" to
        "Barre devant les cuisses, prise largeur d’épaules, genoux légèrement fléchis. Envoie les hanches en arrière en gardant le dos plat, la barre glisse le long des jambes jusqu’à mi-tibia, puis reviens en serrant les fessiers. Ne arrondis jamais le bas du dos.",
    "dumbbell-lunges" to
        "Un haltère dans chaque main, bras le long du corps. Fais un grand pas en avant et plie les deux genoux jusqu’à ~90°, buste droit, sans que le genou arrière touche le sol. Pousse sur la jambe avant pour revenir. Alterne les jambes.",
    "bulgarian-split-squat" to
        "Pied arrière posé sur une chaise/un canapé derrière toi, un haltère dans chaque main. Descends sur la jambe avant jusqu’à ce que la cuisse soit parallèle au sol, buste droit, puis remonte en poussant sur le talon avant. Fais l’autre jambe.",
    "hip-thrust" to
        "Haut du dos appuyé sur un banc, barre (rembourrée) sur le pli des hanches, pieds à plat. Décolle le bassin en serrant fort les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer, puis redescends en contrôlant.",
    "glute-bridge" to
        "Allongé sur le dos, genoux fléchis, pieds à plat largeur de bassin, bras le long du corps. Décolle le bassin en serrant les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer. Tiens une seconde en haut puis redescends en contrôlant.",
    "leg-press" to
        "Assis dans la machine, pieds à plat sur la plateforme largeur d’épaules. Déverrouille puis descends en contrôlant jusqu’à ~90° aux genoux sans décoller le bas du dos, puis pousse sans bloquer brutalement les genoux en fin de mouvement.",
    "kettlebell-swing" to
        "Kettlebell à deux mains, pieds un peu plus larges que les épaules. Bascule les hanches vers l’arrière puis projette-les vers l’avant pour propulser le poids à hauteur de poitrine, bras relâchés. La force vient des hanches, pas des bras. Dos gainé tout du long.",
    "bodyweight-squat" to
        "Pieds largeur d’épaules, bras devant pour l’équilibre. Descends en pliant les genoux et en envoyant les hanches vers l’arrière, dos droit et talons au sol, jusqu’aux cuisses parallèles, puis remonte. Idéal pour apprendre le mouvement.",
    "standing-calf-raise" to
        "Debout, pointe des pieds sur une marche ou à plat, un haltère en main si tu charges. Monte le plus haut possible sur la pointe des pieds en contractant les mollets, marque un temps en haut, puis redescends lentement en étirant.",
    "dumbbell-bench-press" to
        "Allongé sur le dos (banc ou sol), un haltère dans chaque main au niveau de la poitrine, coudes ouverts. Pousse les haltères vers le plafond bras tendus, puis redescends lentement.",
    "barbell-bench-press" to
        "Allongé sur un banc, barre au-dessus de la poitrine, prise un peu plus large que les épaules. Descends la barre en contrôlant jusqu’au bas des pectoraux, coudes à ~45°, puis pousse jusqu’aux bras tendus. Garde les omoplates serrées et les pieds au sol.",
    "incline-dumbbell-press" to
        "Sur un banc incliné (~30°), un haltère dans chaque main à hauteur du haut des pectoraux. Pousse vers le plafond bras tendus sans cogner les haltères, puis redescends lentement en ouvrant légèrement les coudes.",
    "push-up" to
        "Mains au sol un peu plus larges que les épaules, corps gainé et aligné de la tête aux talons. Descends en pliant les coudes (~45° du corps) jusqu’à frôler le sol, puis pousse pour remonter. Trop dur ? Pose les genoux au sol.",
    "dumbbell-fly" to
        "Allongé sur un banc, un haltère léger dans chaque main au-dessus de la poitrine, coudes légèrement fléchis et fixes. Ouvre les bras en arc de cercle jusqu’à sentir l’étirement des pectoraux, puis reviens en « refermant » sans tendre les coudes.",
    "band-chest-press" to
        "Élastique passé dans le dos et tenu à deux mains à hauteur de poitrine. Pousse les mains vers l’avant jusqu’aux bras tendus en contractant les pectoraux, puis reviens en contrôlant la tension de l’élastique.",
    "one-arm-dumbbell-row" to
        "Un genou et une main en appui sur une table ou un banc, dos plat et horizontal. Bras tendu vers le sol, tire l’haltère le long du flanc vers la hanche, coude près du corps, puis redescends. Fais l’autre bras.",
    "bent-over-two-dumbbell-row" to
        "Buste penché en avant (~45°), dos plat, genoux légèrement fléchis, haltères sous les épaules bras tendus. Tire les deux haltères vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant.",
    "barbell-row" to
        "Buste penché (~45°), dos plat, barre bras tendus sous les épaules. Tire la barre vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant. Garde le bas du dos gainé.",
    "deadlift" to
        "Barre au sol contre les tibias, pieds largeur de bassin. Attrape la barre, dos plat et poitrine haute, puis pousse dans le sol en tendant hanches et genoux ensemble, barre collée aux jambes. Verrouille debout sans tirer en arrière. Mouvement technique : reste léger au début.",
    "pull-up" to
        "Suspendu à une barre, prise largeur d’épaules (paumes vers l’avant). Tire en amenant la poitrine vers la barre, coudes vers le bas, puis redescends en contrôlant bras tendus. Trop dur ? Aide-toi d’un élastique ou d’un appui des pieds.",
    "lat-pulldown" to
        "Assis face à la poulie haute, cuisses bloquées, prise large. Tire la barre vers le haut de la poitrine en serrant les omoplates et en gardant le buste légèrement incliné en arrière, puis remonte en contrôlant.",
    "band-row" to
        "Élastique fixé devant toi à hauteur de poitrine (ou autour des pieds, jambes tendues). Tire les poignées vers le bas des côtes en serrant les omoplates, coudes près du corps, puis reviens en contrôlant la tension.",
    "scapular-retraction" to
        "Debout ou assis, bras le long du corps (ou tenant un élastique devant toi). Serre les omoplates l’une vers l’autre vers le bas, sans hausser les épaules ni cambrer. Tiens 2 s puis relâche. Ouvre les épaules, contre la position assise fermée.",
    "standing-dumbbell-press" to
        "Debout, gainé, un haltère de chaque côté à hauteur d’épaules, paumes vers l’avant. Pousse les haltères au-dessus de la tête bras tendus sans cambrer le dos, puis redescends à hauteur d’épaules.",
    "overhead-press-barbell" to
        "Debout, barre sur le haut des pectoraux, prise largeur d’épaules, gainage serré. Pousse la barre au-dessus de la tête bras tendus en rentrant légèrement la tête, sans cambrer le bas du dos, puis redescends en contrôlant.",
    "lateral-raise" to
        "Debout, un haltère léger dans chaque main le long du corps, coudes à peine fléchis. Lève les bras sur les côtés jusqu’à hauteur d’épaules (pas plus haut), comme pour « verser de l’eau », puis redescends lentement. Mouvement contrôlé, sans élan.",
    "rear-delt-fly" to
        "Buste penché en avant, dos plat, un haltère léger dans chaque main sous la poitrine. Ouvre les bras sur les côtés en serrant les omoplates, coudes à peine fléchis, jusqu’à hauteur d’épaules, puis reviens en contrôlant. Cible l’arrière de l’épaule.",
    "band-face-pull" to
        "Élastique fixé à hauteur du visage. Tire les deux brins vers ton front en écartant les mains et en serrant les omoplates, coudes hauts, puis reviens en contrôlant. Excellent pour la posture et la santé des épaules.",
    "dumbbell-biceps-curl" to
        "Debout, un haltère dans chaque main, bras le long du corps, paumes vers l’avant. Plie les coudes pour monter les haltères vers les épaules sans bouger les coudes ni balancer le buste, puis redescends lentement.",
    "hammer-curl" to
        "Comme un curl, mais paumes face à face (prise marteau) tout du long. Monte les haltères vers les épaules sans bouger les coudes, puis redescends en contrôlant. Sollicite biceps et avant-bras.",
    "triceps-overhead-extension" to
        "Debout ou assis, un haltère tenu à deux mains au-dessus de la tête, bras tendus. Descends l’haltère derrière la nuque en pliant les coudes (qui restent hauts et serrés), puis tends les bras pour remonter. Contrôle la descente.",
    "triceps-dips-bench" to
        "Mains posées au bord d’une chaise/banc derrière toi, jambes devant. Descends le bassin en pliant les coudes vers l’arrière jusqu’à ~90°, puis pousse pour remonter en tendant les bras. Plie les genoux pour faciliter, tends-les pour durcir.",
    "band-biceps-curl" to
        "Debout sur l’élastique, une poignée dans chaque main, bras le long du corps. Plie les coudes pour monter les mains vers les épaules contre la tension, sans bouger les coudes, puis redescends en contrôlant.",
    "plank" to
        "En appui sur les avant-bras et la pointe des pieds, corps gainé et bien aligné des épaules aux talons (pas de creux dans le bas du dos, fesses ni trop hautes ni trop basses). Tiens la position en respirant.",
    "side-plank" to
        "Allongé sur le côté, en appui sur un avant-bras (coude sous l’épaule), corps aligné de la tête aux pieds. Décolle les hanches et tiens la position sans laisser le bassin tomber. Puis change de côté.",
    "dead-bug" to
        "Allongé sur le dos, bras tendus vers le plafond et genoux fléchis au-dessus des hanches (90°). Garde le bas du dos plaqué au sol et descends lentement le bras droit derrière la tête et la jambe gauche vers le sol, sans creuser les lombaires. Reviens et alterne.",
    "bird-dog" to
        "À quatre pattes, mains sous les épaules et genoux sous les hanches, dos plat. Tends en même temps le bras droit devant et la jambe gauche derrière, alignés avec le tronc, sans cambrer ni tourner le bassin. Reviens et alterne. Gainage anti-rotation très sûr.",
    "mcgill-curl-up" to
        "Allongé sur le dos, une jambe pliée pied au sol et l’autre tendue. Place tes mains sous le bas du dos pour garder sa courbure naturelle. Décolle juste la tête et les épaules de quelques centimètres sans plier la colonne, menton rentré, puis redescends doucement.",
    "superman" to
        "Allongé sur le ventre, bras tendus devant. Décolle légèrement bras, poitrine et jambes du sol en gardant le regard vers le bas et la nuque longue, sur une amplitude réduite. Tiens brièvement puis relâche. À n’introduire que sans aucune douleur.",
)

/** Texte d'exécution (« comment faire ») d'un exercice, ou `null`. */
fun exerciseHowTo(id: String): String? = HOWTO[id]
