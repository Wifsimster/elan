// Tests du catalogue et du moteur de recommandation (Exercises.kt) : charges
// conseillées par objectif / sexe / poids de corps, exercices chronométrés et au
// poids du corps, pas d'arrondi, recherche, intégrité du catalogue.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisesTest {
    private fun profile(
        weightKg: Double = 80.0,
        sex: Sex? = Sex.H,
        goal: TrainingGoal = TrainingGoal.HYPERTROPHIE,
    ) = RecoProfile(weightKg = weightKg, heightCm = 180.0, sex = sex, goal = goal)

    private fun ex(id: String): CatalogExercise = catalogById(id) ?: error("exercice inconnu : $id")

    // --- Catalogue -----------------------------------------------------------

    @Test
    fun `42 fiches, ids uniques, chacune avec un texte d'exécution`() {
        assertEquals(42, CATALOG.size)
        assertEquals(42, CATALOG.map { it.id }.toSet().size)
        assertEquals(42, CATALOG.map { it.name }.toSet().size)
        for (e in CATALOG) {
            val howTo = exerciseHowTo(e.id)
            assertNotNull("howTo manquant pour ${e.id}", howTo)
            assertTrue("howTo vide pour ${e.id}", howTo!!.isNotEmpty())
            assertTrue("muscles vides pour ${e.id}", e.muscles.isNotEmpty())
            assertTrue("matériel vide pour ${e.id}", e.equipment.isNotEmpty())
        }
        assertNull(exerciseHowTo("inconnu"))
    }

    @Test
    fun `répartition par rayon et ordre des rayons`() {
        assertEquals(listOf("Jambes", "Pectoraux", "Dos", "Épaules", "Bras", "Gainage"), CATEGORIES.map { it.label })
        val byCat = CATALOG.groupingBy { it.category }.eachCount()
        assertEquals(12, byCat[ExerciseCategory.JAMBES])
        assertEquals(6, byCat[ExerciseCategory.PECTORAUX])
        assertEquals(8, byCat[ExerciseCategory.DOS])
        assertEquals(5, byCat[ExerciseCategory.EPAULES])
        assertEquals(5, byCat[ExerciseCategory.BRAS])
        assertEquals(6, byCat[ExerciseCategory.GAINAGE])
        assertEquals(
            listOf("poids du corps", "haltères", "barre", "kettlebell", "élastique", "machine"),
            EQUIPMENTS.map { it.label },
        )
    }

    @Test
    fun `le gainage est toujours au poids du corps, les chronométrés sont plank et side-plank`() {
        for (e in CATALOG.filter { it.category == ExerciseCategory.GAINAGE }) assertEquals(0.0, e.loadFactor, 0.0)
        assertEquals(listOf("plank", "side-plank"), CATALOG.filter { it.timed }.map { it.id })
        assertEquals("côté", ex("side-plank").perSideLabel)
    }

    @Test
    fun `les textes communs avec le programme sont repris à l'identique`() {
        for ((id, name) in listOf("goblet-squat" to "Goblet squat", "plank" to "Gainage planche", "dumbbell-bench-press" to "Développé couché haltères")) {
            assertEquals(exerciseByName(name)?.howTo, exerciseHowTo(id))
        }
    }

    // --- GOALS / goalSpec ----------------------------------------------------

    @Test
    fun `cinq objectifs dans l'ordre, repli sur l'hypertrophie`() {
        assertEquals(
            listOf(TrainingGoal.FORCE, TrainingGoal.HYPERTROPHIE, TrainingGoal.ENDURANCE, TrainingGoal.TONIFICATION, TrainingGoal.PERTE_POIDS),
            GOALS.map { it.id },
        )
        assertEquals("Prise de muscle", goalLabel(TrainingGoal.HYPERTROPHIE))
        assertEquals("Remise en forme", goalLabel(TrainingGoal.TONIFICATION))
        assertSame(GOALS[1], goalSpec(null))
        val force = goalSpec(TrainingGoal.FORCE)
        assertEquals(5, force.sets)
        assertEquals(3, force.repsMin)
        assertEquals(6, force.repsMax)
        assertEquals(180, force.restSec)
        assertEquals(1.15, force.intensityFactor, 0.0)
    }

    // --- recommend -----------------------------------------------------------

    @Test
    fun `charge = loadFactor × poids × intensité, arrondie au pas`() {
        // goblet-squat : 0,3 × 80 × 1,0 = 24 kg (bas du corps, pas de 1 kg)
        val r = recommend(profile(), ex("goblet-squat"))
        assertEquals(Recommendation(sets = 4, repsMin = 8, repsMax = 12, restSec = 90, weightKg = 24.0, perDumbbell = false, timed = false), r)
        // force : 0,3 × 80 × 1,15 = 27,6 → 28 kg, 5 × 3-6, repos 180 s
        val f = recommend(profile(goal = TrainingGoal.FORCE), ex("goblet-squat"))
        assertEquals(28.0, f.weightKg, 0.0)
        assertEquals(5, f.sets)
        assertEquals(3, f.repsMin)
        assertEquals(6, f.repsMax)
        assertEquals(180, f.restSec)
        // endurance : 0,3 × 80 × 0,65 = 15,6 → 16 kg, 3 × 15-20, repos 45 s
        val e = recommend(profile(goal = TrainingGoal.ENDURANCE), ex("goblet-squat"))
        assertEquals(16.0, e.weightKg, 0.0)
        assertEquals(3, e.sets)
        assertEquals(15, e.repsMin)
        assertEquals(20, e.repsMax)
        assertEquals(45, e.restSec)
    }

    @Test
    fun `la charge suit le poids de corps`() {
        assertEquals(18.0, recommend(profile(weightKg = 60.0), ex("goblet-squat")).weightKg, 0.0) // 0,3 × 60
        assertEquals(30.0, recommend(profile(weightKg = 100.0), ex("goblet-squat")).weightKg, 0.0) // 0,3 × 100
    }

    @Test
    fun `facteur sexe - femme 0,65 haut du corps et 0,8 bas du corps, homme ou non précisé 1`() {
        // dumbbell-bench-press (haut, par haltère) : 0,22 × 80 = 17,6 → 18 ; femme 17,6 × 0,65 = 11,44 → 11
        assertEquals(18.0, recommend(profile(sex = Sex.H), ex("dumbbell-bench-press")).weightKg, 0.0)
        assertEquals(18.0, recommend(profile(sex = null), ex("dumbbell-bench-press")).weightKg, 0.0)
        assertEquals(11.0, recommend(profile(sex = Sex.F), ex("dumbbell-bench-press")).weightKg, 0.0)
        // goblet-squat (bas) : femme 24 × 0,8 = 19,2 → 19
        assertEquals(19.0, recommend(profile(sex = Sex.F), ex("goblet-squat")).weightKg, 0.0)
        // back-squat (barre, bas) : 80 → pas de 2,5 ; femme 64 → 65
        assertEquals(80.0, recommend(profile(), ex("back-squat")).weightKg, 0.0)
        assertEquals(65.0, recommend(profile(sex = Sex.F), ex("back-squat")).weightKg, 0.0)
    }

    @Test
    fun `mouvements aux haltères - charge par haltère`() {
        val r = recommend(profile(), ex("dumbbell-bench-press"))
        assertTrue(r.perDumbbell)
        assertEquals("18 kg / main", recoWeightLabel(r))
        assertFalse(recommend(profile(), ex("back-squat")).perDumbbell)
        assertEquals("80 kg", recoWeightLabel(recommend(profile(), ex("back-squat"))))
    }

    @Test
    fun `exercice chronométré - pas de charge, reps en secondes selon l'objectif`() {
        val r = recommend(profile(goal = TrainingGoal.FORCE), ex("plank"))
        assertEquals(Recommendation(sets = 5, repsMin = 30, repsMax = 60, restSec = 180, weightKg = 0.0, perDumbbell = false, timed = true), r)
        assertEquals("gainage chronométré", recoWeightLabel(r))
        assertEquals("5 × 30-60 s", recoHint(ex("plank"), r))
        assertEquals("5 × 30-60 s / côté", recoHint(ex("side-plank"), recommend(profile(goal = TrainingGoal.FORCE), ex("side-plank"))))
        val e = recommend(profile(goal = TrainingGoal.ENDURANCE), ex("plank"))
        assertEquals(45, e.repsMin)
        assertEquals(90, e.repsMax)
    }

    @Test
    fun `exercice au poids du corps - charge nulle, reps de l'objectif`() {
        val r = recommend(profile(), ex("push-up"))
        assertEquals(Recommendation(sets = 4, repsMin = 8, repsMax = 12, restSec = 90, weightKg = 0.0, perDumbbell = false, timed = false), r)
        assertEquals("au poids du corps", recoWeightLabel(r))
        assertEquals("4 × 8-12", recoHint(ex("push-up"), r))
        assertEquals("4 × 8-12 / côté", recoHint(ex("dead-bug"), recommend(profile(), ex("dead-bug"))))
    }

    @Test
    fun `la taille n'entre pas dans le calcul`() {
        val a = recommend(profile().copy(heightCm = 160.0), ex("goblet-squat"))
        val b = recommend(profile().copy(heightCm = 200.0), ex("goblet-squat"))
        assertEquals(a, b)
    }

    @Test
    fun `recommend accepte un profil complet`() {
        val p = Profile(weightKg = 80.0, heightCm = 180.0, maxHr = 185.0, goal = TrainingGoal.HYPERTROPHIE, sex = Sex.H)
        assertEquals(recommend(profile(), ex("goblet-squat")), recommend(p, ex("goblet-squat")))
    }

    // --- roundWeight ---------------------------------------------------------

    @Test
    fun `roundWeight - pas de 2,5 kg pour les charges lourdes non par haltère`() {
        val barre = ex("back-squat") // loadFactor 1.0, pas par haltère
        assertEquals(60.0, roundWeight(61.0, barre), 0.0)
        assertEquals(62.5, roundWeight(61.3, barre), 0.0)
        assertEquals(2.5, roundWeight(2.0, barre), 0.0)
    }

    @Test
    fun `roundWeight - pas de 1 kg au-delà de 6 kg, demi-kilo en dessous`() {
        val leger = ex("goblet-squat") // loadFactor 0.3
        assertEquals(7.0, roundWeight(7.4, leger), 0.0)
        assertEquals(8.0, roundWeight(7.5, leger), 0.0)
        assertEquals(3.5, roundWeight(3.3, leger), 0.0)
        assertEquals(5.5, roundWeight(5.6, leger), 0.0)
        // par haltère, même lourd : jamais le pas de 2,5
        val parHaltere = leger.copy(loadFactor = 1.0, loadPerSide = true)
        assertEquals(61.0, roundWeight(61.3, parHaltere), 0.0)
    }

    @Test
    fun `roundWeight - charge nulle ou négative donne 0`() {
        assertEquals(0.0, roundWeight(0.0, ex("goblet-squat")), 0.0)
        assertEquals(0.0, roundWeight(-3.0, ex("goblet-squat")), 0.0)
    }

    @Test
    fun `très léger - élévations latérales au demi-kilo`() {
        // 0,06 × 80 = 4,8 → pas 0,5 → 5 kg / main
        val r = recommend(profile(), ex("lateral-raise"))
        assertEquals(5.0, r.weightKg, 0.0)
        assertEquals("5 kg / main", recoWeightLabel(r))
        // charge décimale : virgule française
        assertEquals("17,5 kg", recoWeightLabel(Recommendation(4, 8, 12, 90, 17.5, perDumbbell = false, timed = false)))
    }

    // --- catalogById / catalogByName -----------------------------------------

    @Test
    fun `catalogById et catalogByName retrouvent les fiches, null sinon`() {
        assertEquals("Goblet squat", catalogById("goblet-squat")?.name)
        assertEquals("goblet-squat", catalogByName("Goblet squat")?.id)
        assertNull(catalogById("inexistant"))
        assertNull(catalogById(null))
        assertNull(catalogById(""))
        assertNull(catalogByName("Inconnu"))
        assertNull(catalogByName(null))
        for (e in CATALOG) {
            assertSame(e, catalogById(e.id))
            assertSame(e, catalogByName(e.name))
        }
    }
}
