// Tests pour les utilitaires du programme muscu (templates, planning, helpers).
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTest {
    private val repos = mapOf("kind" to "repos")
    private fun plan(entry: Any?): List<Any?> = listOf(entry) + List(6) { repos }

    // --- templateById --------------------------------------------------------

    @Test
    fun `retrouve un template existant`() {
        val t = templateById("fullbody-a")
        assertNotNull(t)
        assertEquals(TemplateId.FULLBODY_A, t?.id)
        assertEquals("Full-body A", t?.name)
    }

    @Test
    fun `retourne null pour un id inconnu ou non défini`() {
        assertNull(templateById("inexistant"))
        assertNull(templateById(null))
    }

    @Test
    fun `chaque template du tableau est retrouvable par son id`() {
        for (t in TEMPLATES) {
            assertSame(t, templateById(t.id.key))
            assertSame(t, templateById(t.id))
        }
    }

    @Test
    fun `quatre programmes et dix-neuf exercices, seuls les full-body progressent`() {
        assertEquals(4, TEMPLATES.size)
        assertEquals(19, TEMPLATES.sumOf { it.exercises.size })
        assertEquals(listOf("Mardi", "Vendredi", "Renfort doux", "Posture assise"), TEMPLATES.map { it.day })
        for (t in TEMPLATES) {
            val auto = t.id == TemplateId.FULLBODY_A || t.id == TemplateId.FULLBODY_B
            for (ex in t.exercises) {
                assertEquals("autoProgress de ${ex.name}", auto, ex.autoProgress != null)
                assertTrue("howTo vide pour ${ex.name}", ex.howTo.isNotEmpty())
                assertTrue("muscles vides pour ${ex.name}", ex.muscles.isNotEmpty())
            }
        }
        assertEquals(ProgressKind.TIME, exerciseByName("Gainage planche")?.autoProgress)
        assertEquals(ProgressKind.LOAD, exerciseByName("Goblet squat")?.autoProgress)
    }

    // --- exerciseByName ------------------------------------------------------

    @Test
    fun `exerciseByName retrouve une fiche du programme, null sinon`() {
        val ex = exerciseByName("Fentes bulgares haltères")
        assertEquals("jambe", ex?.perSideLabel)
        assertEquals(12.0, ex!!.startWeightKg, 0.0)
        assertNull(exerciseByName("Inconnu"))
        assertNull(exerciseByName(null))
        assertNull(exerciseByName(""))
    }

    // --- planForDay ----------------------------------------------------------
    // Convention : 0=dimanche (Date.getDay()), index interne 0=lundi.

    @Test
    fun `lundi (1) - vélo`() {
        assertEquals("velo", planForDay(1).kind)
    }

    @Test
    fun `mardi (2) - muscu Full-body A`() {
        val p = planForDay(2)
        assertTrue(p is PlannedSession.Muscu)
        assertEquals(TemplateId.FULLBODY_A, (p as PlannedSession.Muscu).templateId)
    }

    @Test
    fun `vendredi (5) - muscu Full-body B`() {
        val p = planForDay(5)
        assertTrue(p is PlannedSession.Muscu)
        assertEquals(TemplateId.FULLBODY_B, (p as PlannedSession.Muscu).templateId)
    }

    @Test
    fun `dimanche (0) - repos`() {
        assertEquals(PlannedSession.Repos, planForDay(0))
    }

    @Test
    fun `samedi (6) - repos`() {
        assertEquals("repos", planForDay(6).kind)
    }

    @Test
    fun `plan hebdomadaire complet de 7 jours`() {
        assertEquals(7, WEEK_PLAN.size)
        assertEquals("Vélo 1h", (DEFAULT_WEEK_PLAN[0] as PlannedSession.Outing).label)
    }

    // --- targetHint ----------------------------------------------------------

    private val base = TemplateExercise(
        name = "X",
        sets = 3,
        repsMin = 8,
        repsMax = 12,
        startWeightKg = 10.0,
        howTo = "",
        muscles = emptyList(),
        icon = "dumbbell",
    )

    @Test
    fun `plage de reps standard`() {
        assertEquals("3 × 8-12", targetHint(base))
    }

    @Test
    fun `reps fixes (min = max) - valeur unique`() {
        assertEquals("3 × 10", targetHint(base.copy(repsMin = 10, repsMax = 10)))
    }

    @Test
    fun `exercice chronométré ajoute s`() {
        assertEquals("3 × 20-40 s", targetHint(base.copy(timed = true, repsMin = 20, repsMax = 40)))
    }

    @Test
    fun `travail unilatéral ajoute le suffixe par côté`() {
        assertEquals("3 × 8-12 / bras", targetHint(base.copy(perSideLabel = "bras")))
    }

    @Test
    fun `chronométré + unilatéral combinés`() {
        assertEquals(
            "3 × 15-30 s / côté",
            targetHint(base.copy(timed = true, repsMin = 15, repsMax = 30, perSideLabel = "côté")),
        )
    }

    // --- isValidWeekPlan -----------------------------------------------------

    @Test
    fun `accepte le planning par défaut`() {
        assertTrue(isValidWeekPlan(DEFAULT_WEEK_PLAN))
    }

    @Test
    fun `rejette autre chose qu'un tableau de 7 entrées`() {
        assertFalse(isValidWeekPlan(null))
        assertFalse(isValidWeekPlan("repos"))
        assertFalse(isValidWeekPlan(listOf(repos))) // trop court
        assertFalse(isValidWeekPlan(List(8) { repos })) // trop long
    }

    @Test
    fun `accepte une muscu pour chacun des templates connus`() {
        for (t in TEMPLATES) {
            assertTrue(isValidWeekPlan(plan(mapOf("kind" to "muscu", "label" to t.name, "templateId" to t.id.key))))
            assertTrue(isValidWeekPlan(plan(PlannedSession.Muscu(t.name, t.id))))
        }
    }

    @Test
    fun `rejette une muscu dont le templateId est inconnu`() {
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "muscu", "label" to "X", "templateId" to "inexistant"))))
    }

    @Test
    fun `rejette une entrée de type inconnu ou un vélo sans label`() {
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "natation"))))
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "velo"))))
    }

    @Test
    fun `weekPlanFrom type un planning désérialisé`() {
        val raw = listOf(
            mapOf("kind" to "velo", "label" to "Vélo 1h"),
            mapOf("kind" to "muscu", "label" to "Full-body A", "templateId" to "fullbody-a"),
            repos,
            repos,
            mapOf("kind" to "muscu", "label" to "Full-body B", "templateId" to "fullbody-b"),
            repos,
            repos,
        )
        assertEquals(DEFAULT_WEEK_PLAN, weekPlanFrom(raw))
        assertNull(weekPlanFrom(listOf(repos)))
        assertNull(weekPlanFrom(plan(mapOf("kind" to "natation"))))
    }

    // --- defaultReps ---------------------------------------------------------

    @Test
    fun `milieu de fourchette, arrondi`() {
        assertEquals(10, defaultReps(base))
    }

    @Test
    fun `plage symétrique impaire arrondie au plus proche`() {
        assertEquals(7, defaultReps(base.copy(repsMin = 5, repsMax = 8))) // 6.5 → 7
    }

    @Test
    fun `min = max - renvoie cette valeur`() {
        assertEquals(10, defaultReps(base.copy(repsMin = 10, repsMax = 10)))
    }

    // --- isValidWeekPlan — activités à pied ----------------------------------

    @Test
    fun `accepte la course et la marche comme le vélo`() {
        assertTrue(isValidWeekPlan(plan(mapOf("kind" to "course", "label" to "Footing 45 min"))))
        assertTrue(isValidWeekPlan(plan(mapOf("kind" to "marche", "label" to "Marche 1 h"))))
    }

    @Test
    fun `exige un libellé`() {
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "course"))))
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "marche", "label" to 42))))
    }

    @Test
    fun `rejette toujours un type inconnu`() {
        assertFalse(isValidWeekPlan(plan(mapOf("kind" to "natation", "label" to "Piscine"))))
    }
}
