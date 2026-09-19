// Programme muscu perso « Maison (haltères) » — prise de muscle.
// Deux séances full-body espacées dans la semaine (mardi A, vendredi B).
// Sert à pré-remplir une séance depuis l'écran muscu : exercices, nombre de
// séries et fourchette de reps en indice. Les charges restent à ajuster à la main
// (c'est le moteur de la progression). Charges de départ volontairement modestes
// pour la montée en charge douce des premières semaines.
//
// La persistance du planning personnalisé (clé `week_plan`) est laissée à la
// couche données ; seule la validation d'une valeur désérialisée vit ici.
package ovh.battistella.elan.domain

import kotlin.math.roundToInt

data class TemplateExercise(
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val startWeightKg: Double,
    /** Explication simple : comment exécuter le mouvement. */
    val howTo: String,
    /** Groupes musculaires principalement sollicités (affichés en pastilles). */
    val muscles: List<String>,
    /**
     * Glyphe MaterialCommunityIcons illustrant le mouvement, posé sur le héros de
     * la fiche d'exercice. Simple chaîne pour garder ce module sans dépendance UI.
     */
    val icon: String,
    /** Clé d'illustration photo (paire départ → fin). Images domaine public bundlées localement. */
    val imageKey: String? = null,
    /** « bras » / « jambe » / « côté » — affiché dans la cible et le travail unilatéral. */
    val perSideLabel: String? = null,
    /** Exercice chronométré (gainage) : les « reps » sont des secondes. */
    val timed: Boolean = false,
    /**
     * Type de progression automatique appliquée d'une semaine sur l'autre selon le
     * ressenti (cf. `AutoProgression.kt`) :
     *  - LOAD : on monte la charge (haltères) par paliers de 2,5 kg ;
     *  - TIME : on allonge le gainage chronométré (secondes), plafonné ;
     *  - null : l'exercice n'est JAMAIS auto-progressé (rééducation, mobilité,
     *           posture — dos/lombaire et cervicales), on garde la prescription.
     * Seuls les deux full-body chargés portent ce drapeau : la progression auto ne
     * touche pas les exercices de renfort doux, par sécurité.
     */
    val autoProgress: ProgressKind? = null,
)

/** Identifiants des 4 programmes intégrés (valeur texte = id persisté dans le planning). */
enum class TemplateId(val key: String) {
    FULLBODY_A("fullbody-a"),
    FULLBODY_B("fullbody-b"),
    DOS_LOMBAIRE("dos-lombaire"),
    CERVICALES("cervicales");

    companion object {
        fun fromKey(key: String?): TemplateId? = entries.firstOrNull { it.key == key }
    }
}

data class WorkoutTemplate(
    val id: TemplateId,
    val name: String,
    val day: String,
    val exercises: List<TemplateExercise>,
)

val TEMPLATES: List<WorkoutTemplate> = listOf(
    WorkoutTemplate(
        id = TemplateId.FULLBODY_A,
        name = "Full-body A",
        day = "Mardi",
        exercises = listOf(
            TemplateExercise(
                name = "Goblet squat",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 20.0, autoProgress = ProgressKind.LOAD,
                icon = "weight-lifter",
                imageKey = "goblet-squat",
                muscles = listOf("Quadriceps", "Fessiers", "Adducteurs", "Gainage"),
                howTo =
                    "Tiens un haltère verticalement contre la poitrine, à deux mains. Pieds largeur d’épaules. Descends en pliant les genoux, dos droit et talons au sol, jusqu’à ce que les cuisses soient parallèles au sol, puis remonte.",
            ),
            TemplateExercise(
                name = "Développé couché haltères",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 16.0, autoProgress = ProgressKind.LOAD,
                icon = "dumbbell",
                imageKey = "dumbbell-bench-press",
                muscles = listOf("Pectoraux", "Triceps", "Deltoïdes antérieurs"),
                howTo =
                    "Allongé sur le dos (banc ou sol), un haltère dans chaque main au niveau de la poitrine, coudes ouverts. Pousse les haltères vers le plafond bras tendus, puis redescends lentement.",
            ),
            TemplateExercise(
                name = "Rowing haltère un bras",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 20.0, perSideLabel = "bras", autoProgress = ProgressKind.LOAD,
                icon = "arm-flex",
                imageKey = "one-arm-dumbbell-row",
                muscles = listOf("Grand dorsal", "Trapèzes", "Rhomboïdes", "Biceps"),
                howTo =
                    "Un genou et une main en appui sur une table ou un banc, dos plat et horizontal. Bras tendu vers le sol, tire l’haltère le long du flanc vers la hanche, coude près du corps, puis redescends. Fais l’autre bras.",
            ),
            TemplateExercise(
                name = "Fentes avant haltères",
                sets = 3, repsMin = 10, repsMax = 10, startWeightKg = 12.0, perSideLabel = "jambe", autoProgress = ProgressKind.LOAD,
                icon = "run",
                imageKey = "dumbbell-lunges",
                muscles = listOf("Quadriceps", "Fessiers", "Ischio-jambiers"),
                howTo =
                    "Un haltère dans chaque main, bras le long du corps. Fais un grand pas en avant et plie les deux genoux jusqu’à ~90°, buste droit, sans que le genou arrière touche le sol. Pousse sur la jambe avant pour revenir. Alterne les jambes.",
            ),
            TemplateExercise(
                name = "Gainage planche",
                sets = 3, repsMin = 20, repsMax = 40, startWeightKg = 0.0, timed = true, autoProgress = ProgressKind.TIME,
                icon = "yoga",
                imageKey = "plank",
                muscles = listOf("Abdominaux", "Transverse", "Lombaires"),
                howTo =
                    "En appui sur les avant-bras et la pointe des pieds, corps gainé et bien aligné des épaules aux talons (pas de creux dans le bas du dos, fesses ni trop hautes ni trop basses). Tiens la position en respirant.",
            ),
        ),
    ),
    WorkoutTemplate(
        id = TemplateId.FULLBODY_B,
        name = "Full-body B",
        day = "Vendredi",
        exercises = listOf(
            TemplateExercise(
                name = "Soulevé de terre roumain haltères",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 20.0, autoProgress = ProgressKind.LOAD,
                icon = "weight-lifter",
                imageKey = "dumbbell-romanian-deadlift",
                muscles = listOf("Ischio-jambiers", "Fessiers", "Lombaires"),
                howTo =
                    "Debout, haltères devant les cuisses, jambes quasi tendues (genoux légèrement fléchis). Penche le buste en envoyant les fesses vers l’arrière, dos bien droit, les haltères descendent le long des jambes jusqu’à sentir l’étirement des ischios, puis reviens en serrant les fessiers.",
            ),
            TemplateExercise(
                name = "Développé épaules debout haltères",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 12.0, autoProgress = ProgressKind.LOAD,
                icon = "dumbbell",
                imageKey = "standing-dumbbell-press",
                muscles = listOf("Deltoïdes", "Triceps", "Gainage"),
                howTo =
                    "Debout, gainé, un haltère de chaque côté à hauteur d’épaules, paumes vers l’avant. Pousse les haltères au-dessus de la tête bras tendus sans cambrer le dos, puis redescends à hauteur d’épaules.",
            ),
            TemplateExercise(
                name = "Rowing penché 2 bras haltères",
                sets = 3, repsMin = 8, repsMax = 12, startWeightKg = 16.0, autoProgress = ProgressKind.LOAD,
                icon = "arm-flex",
                imageKey = "bent-over-two-dumbbell-row",
                muscles = listOf("Grand dorsal", "Trapèzes", "Rhomboïdes", "Biceps"),
                howTo =
                    "Buste penché en avant (~45°), dos plat, genoux légèrement fléchis, haltères sous les épaules bras tendus. Tire les deux haltères vers le bas-ventre en serrant les omoplates, coudes près du corps, puis redescends en contrôlant.",
            ),
            TemplateExercise(
                name = "Fentes bulgares haltères",
                sets = 3, repsMin = 8, repsMax = 10, startWeightKg = 12.0, perSideLabel = "jambe", autoProgress = ProgressKind.LOAD,
                icon = "run",
                imageKey = "bulgarian-split-squat",
                muscles = listOf("Quadriceps", "Fessiers", "Ischio-jambiers"),
                howTo =
                    "Pied arrière posé sur une chaise/un canapé derrière toi, un haltère dans chaque main. Descends sur la jambe avant jusqu’à ce que la cuisse soit parallèle au sol, buste droit, puis remonte en poussant sur le talon avant. Fais l’autre jambe.",
            ),
            TemplateExercise(
                name = "Gainage latéral",
                sets = 3, repsMin = 15, repsMax = 30, startWeightKg = 0.0, timed = true, perSideLabel = "côté", autoProgress = ProgressKind.TIME,
                icon = "yoga",
                imageKey = "side-plank",
                muscles = listOf("Obliques", "Transverse", "Moyen fessier"),
                howTo =
                    "Allongé sur le côté, en appui sur un avant-bras (coude sous l’épaule), corps aligné de la tête aux pieds. Décolle les hanches et tiens la position sans laisser le bassin tomber. Puis change de côté.",
            ),
        ),
    ),
    WorkoutTemplate(
        id = TemplateId.DOS_LOMBAIRE,
        name = "Dos / lombaire",
        day = "Renfort doux",
        exercises = listOf(
            TemplateExercise(
                name = "Bird-dog",
                sets = 3, repsMin = 8, repsMax = 10, startWeightKg = 0.0, perSideLabel = "côté",
                icon = "dog",
                muscles = listOf("Gainage", "Transverse", "Lombaires", "Fessiers"),
                howTo =
                    "À quatre pattes (quadrupédie), mains sous les épaules et genoux sous les hanches, dos plat. Tends en même temps le bras droit devant et la jambe gauche derrière, alignés avec le tronc, sans cambrer ni tourner le bassin. Reviens et alterne avec l’autre côté. Gainage anti-rotation, très sûr pour la colonne.",
            ),
            TemplateExercise(
                name = "Dead bug",
                sets = 3, repsMin = 8, repsMax = 10, startWeightKg = 0.0, perSideLabel = "côté",
                icon = "bug",
                muscles = listOf("Transverse", "Abdominaux", "Gainage"),
                howTo =
                    "Allongé sur le dos, bras tendus vers le plafond et genoux fléchis au-dessus des hanches (90°). Garde le bas du dos plaqué au sol et descends lentement le bras droit derrière la tête et la jambe gauche vers le sol, sans creuser les lombaires. Reviens et alterne. Renforce le transverse sans charger les disques.",
            ),
            TemplateExercise(
                name = "Pont fessier",
                sets = 3, repsMin = 12, repsMax = 15, startWeightKg = 0.0,
                icon = "bridge",
                muscles = listOf("Fessiers", "Lombaires", "Ischio-jambiers"),
                howTo =
                    "Allongé sur le dos, genoux fléchis, pieds à plat largeur de bassin, bras le long du corps. Décolle le bassin en serrant les fessiers jusqu’à aligner épaules-hanches-genoux, sans cambrer. Tiens une seconde en haut puis redescends en contrôlant. Renforce fessiers et lombaires, soulage le bas du dos.",
            ),
            TemplateExercise(
                name = "McGill curl-up",
                sets = 3, repsMin = 6, repsMax = 8, startWeightKg = 0.0,
                icon = "human",
                muscles = listOf("Abdominaux", "Transverse", "Gainage"),
                howTo =
                    "Allongé sur le dos, une jambe pliée pied au sol et l’autre tendue. Place tes mains sous le bas du dos pour garder sa courbure naturelle. Décolle juste la tête et les épaules de quelques centimètres sans plier la colonne, menton rentré, puis redescends doucement. Gainage abdo qui protège les disques.",
            ),
            TemplateExercise(
                name = "Superman (extension lombaire)",
                sets = 2, repsMin = 8, repsMax = 10, startWeightKg = 0.0,
                icon = "arrow-up-bold",
                muscles = listOf("Lombaires", "Fessiers", "Dos"),
                howTo =
                    "Allongé sur le ventre, bras tendus devant. Décolle légèrement bras, poitrine et jambes du sol en gardant le regard vers le bas et la nuque longue, sur une amplitude réduite. Tiens brièvement puis relâche. À n’introduire que si tu n’as aucune douleur.",
            ),
        ),
    ),
    WorkoutTemplate(
        id = TemplateId.CERVICALES,
        name = "Cervicales / nuque",
        day = "Posture assise",
        exercises = listOf(
            TemplateExercise(
                name = "Rétractions cervicales (chin tucks)",
                sets = 3, repsMin = 10, repsMax = 10, startWeightKg = 0.0,
                icon = "head-outline",
                muscles = listOf("Fléchisseurs profonds du cou", "Cervicales"),
                howTo =
                    "Assis ou debout, regard à l’horizontale. Recule le menton en glissant la tête vers l’arrière (comme pour faire un double menton), sans baisser ni lever le regard. Tiens 2-3 s puis relâche. Corrige la tête en avant — à faire plusieurs fois par jour.",
            ),
            TemplateExercise(
                name = "Étirement trapèze supérieur",
                sets = 2, repsMin = 30, repsMax = 30, startWeightKg = 0.0, timed = true, perSideLabel = "côté",
                icon = "human-handsdown",
                muscles = listOf("Trapèze supérieur", "Cervicales"),
                howTo =
                    "Assis, une main sous la fesse pour abaisser l’épaule. Incline doucement la tête vers le côté opposé (oreille vers l’épaule), en t’aidant légèrement de l’autre main. Étirement doux côté douloureux, sans à-coup. Change de côté.",
            ),
            TemplateExercise(
                name = "Étirement scalènes / SCM",
                sets = 2, repsMin = 20, repsMax = 30, startWeightKg = 0.0, timed = true, perSideLabel = "côté",
                icon = "human-handsdown",
                muscles = listOf("Scalènes", "Sterno-cléido-mastoïdien"),
                howTo =
                    "Assis, épaule basse. Incline la tête sur le côté puis tourne légèrement le menton vers le haut et l’arrière jusqu’à sentir l’étirement sur l’avant-côté du cou. Reste doux et respire. Change de côté.",
            ),
            TemplateExercise(
                name = "Rétractions scapulaires",
                sets = 3, repsMin = 12, repsMax = 15, startWeightKg = 0.0,
                icon = "arrow-collapse-horizontal",
                muscles = listOf("Rhomboïdes", "Trapèzes moyens", "Deltoïdes postérieurs"),
                howTo =
                    "Debout ou assis, bras le long du corps (ou tenant un élastique devant toi). Serre les omoplates l’une vers l’autre vers le bas, sans hausser les épaules ni cambrer. Tiens 2 s puis relâche. Ouvre les épaules, contre la position assise fermée.",
            ),
        ),
    ),
)

// Planning hebdomadaire (télétravail lun/mar/ven). Mardi et vendredi pour la
// muscu (3-4 jours d'écart), vélo le lundi en récup active. Index 0 = lundi.
sealed class PlannedSession {
    /** Valeur texte du `kind` telle que persistée (`velo`, `course`, `marche`, `muscu`, `repos`). */
    abstract val kind: String

    /** Sortie GPS planifiée (`velo` / `course` / `marche`). */
    data class Outing(override val kind: String, val label: String) : PlannedSession() {
        init {
            require(kind in OUTING_KINDS) { "kind de sortie inconnu : $kind" }
        }
    }

    data class Muscu(val label: String, val templateId: TemplateId) : PlannedSession() {
        override val kind: String get() = "muscu"
    }

    data object Repos : PlannedSession() {
        override val kind: String get() = "repos"
    }

    companion object {
        val OUTING_KINDS: Set<String> = setOf("velo", "course", "marche")
    }
}

/** Planning par défaut, utilisé tant que l'utilisateur n'en a pas défini un. */
val DEFAULT_WEEK_PLAN: List<PlannedSession> = listOf(
    PlannedSession.Outing("velo", "Vélo 1h"),
    PlannedSession.Muscu("Full-body A", TemplateId.FULLBODY_A),
    PlannedSession.Repos,
    PlannedSession.Repos,
    PlannedSession.Muscu("Full-body B", TemplateId.FULLBODY_B),
    PlannedSession.Repos,
    PlannedSession.Repos,
)

/** Conservé pour compat ascendante (export brut et anciens appels). */
val WEEK_PLAN: List<PlannedSession> = DEFAULT_WEEK_PLAN

/** Clé de réglage sous laquelle la couche données persiste le planning personnalisé. */
const val WEEK_PLAN_SETTING_KEY = "week_plan"

/** Vrai si `id` correspond à un template muscu connu (les 4 programmes). */
private fun isKnownTemplateId(id: Any?): Boolean =
    id is String && TEMPLATES.any { it.id.key == id }

/**
 * Valide une valeur désérialisée (liste de dictionnaires `kind`/`label`/`templateId`,
 * ou déjà des `PlannedSession`) : 7 entrées, chacune bien formée. Pour la muscu,
 * on accepte n'importe quel template connu (pas seulement full-body A/B) afin
 * qu'un plan restauré depuis une sauvegarde ne soit pas silencieusement rejeté.
 */
fun isValidWeekPlan(value: Any?): Boolean {
    if (value !is List<*> || value.size != 7) return false
    return value.all { entry -> plannedSessionFrom(entry) != null }
}

/** Convertit une entrée désérialisée en `PlannedSession`, ou `null` si mal formée. */
fun plannedSessionFrom(entry: Any?): PlannedSession? {
    if (entry is PlannedSession) return entry
    if (entry !is Map<*, *>) return null
    val kind = entry["kind"]
    val label = entry["label"]
    return when {
        kind == "repos" -> PlannedSession.Repos
        kind is String && kind in PlannedSession.OUTING_KINDS ->
            if (label is String) PlannedSession.Outing(kind, label) else null
        kind == "muscu" -> {
            val templateId = entry["templateId"]
            if (label is String && isKnownTemplateId(templateId)) {
                PlannedSession.Muscu(label, TemplateId.fromKey(templateId as String)!!)
            } else {
                null
            }
        }
        else -> null
    }
}

/** Plan typé à partir d'une valeur désérialisée, ou `null` si elle est invalide. */
fun weekPlanFrom(value: Any?): List<PlannedSession>? {
    if (value !is List<*> || value.size != 7) return null
    val out = ArrayList<PlannedSession>(7)
    for (entry in value) out.add(plannedSessionFrom(entry) ?: return null)
    return out
}

/** Séance prévue pour un jour `Date.getDay()` JS (0 = dimanche … 6 = samedi). */
fun planForDay(jsDay: Int, plan: List<PlannedSession> = DEFAULT_WEEK_PLAN): PlannedSession =
    plan[(jsDay + 6) % 7] // bascule vers lundi = 0

/** Retrouve un template par son id texte (pour le pré-chargement depuis l'accueil). */
fun templateById(id: String?): WorkoutTemplate? = TEMPLATES.firstOrNull { it.id.key == id }

/** Variante typée de [templateById]. */
fun templateById(id: TemplateId): WorkoutTemplate = TEMPLATES.first { it.id == id }

/**
 * Retrouve la fiche d'un exercice du programme par son nom (illustration,
 * muscles, exécution). Utilisé par la page de progression pour enrichir le
 * détail d'un exercice issu d'une séance enregistrée.
 */
fun exerciseByName(name: String?): TemplateExercise? {
    if (name.isNullOrEmpty()) return null
    for (t in TEMPLATES) {
        val found = t.exercises.firstOrNull { it.name == name }
        if (found != null) return found
    }
    return null
}

/** Indice de cible affiché sous le nom de l'exercice, ex. « 3 × 8-12 / bras ». */
fun targetHint(ex: TemplateExercise): String {
    val reps = if (ex.repsMin == ex.repsMax) "${ex.repsMin}" else "${ex.repsMin}-${ex.repsMax}"
    val unit = if (ex.timed) " s" else ""
    val side = if (ex.perSideLabel != null) " / ${ex.perSideLabel}" else ""
    return "${ex.sets} × $reps$unit$side"
}

/** Valeur de départ pré-remplie pour les reps (milieu de la fourchette). */
fun defaultReps(ex: TemplateExercise): Int = ((ex.repsMin + ex.repsMax) / 2.0).roundToInt()
