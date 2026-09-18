// Tests de la progression automatique (AutoProgression.kt) : cœur pur
// (nextTarget, targetForExercise, isoWeekKey, descriptions, validation).
// L'orchestration impure (lecture historique / persistance / notif) n'est pas
// portée dans le domaine.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AutoProgressionTest {
    // --- Fabriques de test -------------------------------------------------

    private fun point(
        maxWeightKg: Double = 20.0,
        topReps: Int = 10,
        difficulty: Difficulty?,
    ) = ExercisePoint(
        sessionId = 1,
        startedAt = 0,
        maxWeightKg = maxWeightKg,
        topReps = topReps,
        volume = 0.0,
        sets = 3,
        difficulty = difficulty,
    )

    private val loadEx = TemplateExercise(
        name = "Goblet squat",
        sets = 3,
        repsMin = 8,
        repsMax = 12,
        startWeightKg = 20.0,
        autoProgress = ProgressKind.LOAD,
        icon = "x",
        muscles = emptyList(),
        howTo = "",
    )

    private val timedEx = TemplateExercise(
        name = "Gainage planche",
        sets = 3,
        repsMin = 20,
        repsMax = 40,
        startWeightKg = 0.0,
        timed = true,
        autoProgress = ProgressKind.TIME,
        icon = "x",
        muscles = emptyList(),
        howTo = "",
    )

    // pas d'autoProgress -> jamais progressé
    private val rehabEx = TemplateExercise(
        name = "Bird-dog",
        sets = 3,
        repsMin = 8,
        repsMax = 10,
        startWeightKg = 0.0,
        icon = "x",
        muscles = emptyList(),
        howTo = "",
    )

    // --- nextTarget --------------------------------------------------------

    @Test
    fun `nextTarget monte d'un pas (2,5 kg) quand le ressenti conseille d'augmenter`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.FACILE, Difficulty.FACILE), base = 20.0, floor = 20.0, ceil = 60.0)
        assertEquals(TargetResult(value = 22.5, base = 20.0, changed = true, direction = Direction.UP), r)
    }

    @Test
    fun `nextTarget allège d'un pas quand la dernière séance est dur`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.FACILE, Difficulty.DUR), base = 25.0, floor = 20.0, ceil = 60.0)
        assertEquals(TargetResult(value = 22.5, base = 25.0, changed = true, direction = Direction.DOWN), r)
    }

    @Test
    fun `nextTarget maintient sur ressenti moyen dominant`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.MOYEN, Difficulty.MOYEN), base = 20.0, floor = 20.0, ceil = 60.0)
        assertFalse(r.changed)
        assertEquals(20.0, r.value, 0.0)
    }

    @Test
    fun `nextTarget maintient quand aucun ressenti n'est noté`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(null, null), base = 20.0, floor = 20.0, ceil = 60.0)
        assertFalse(r.changed)
    }

    @Test
    fun `nextTarget ne dépasse jamais le plafond`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.FACILE), base = 60.0, floor = 20.0, ceil = 60.0)
        assertFalse(r.changed)
        assertEquals(60.0, r.value, 0.0)
    }

    @Test
    fun `nextTarget borne la montée au plafond`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.FACILE), base = 59.0, floor = 20.0, ceil = 60.0)
        assertTrue(r.changed)
        assertEquals(60.0, r.value, 0.0)
    }

    @Test
    fun `nextTarget ne descend jamais sous le plancher`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.DUR, Difficulty.DUR), base = 20.0, floor = 20.0, ceil = 60.0)
        assertFalse(r.changed)
        assertEquals(20.0, r.value, 0.0)
    }

    @Test
    fun `nextTarget progresse le gainage de +5 s`() {
        val r = nextTarget(ProgressKind.TIME, listOf(Difficulty.FACILE), base = 30.0, floor = 20.0, ceil = 60.0)
        assertEquals(TargetResult(value = 35.0, base = 30.0, changed = true, direction = Direction.UP), r)
    }

    @Test
    fun `nextTarget accepte un pas explicite`() {
        val r = nextTarget(ProgressKind.LOAD, listOf(Difficulty.FACILE), base = 20.0, floor = 20.0, ceil = 60.0, step = 5.0)
        assertEquals(25.0, r.value, 0.0)
    }

    // --- targetForExercise -------------------------------------------------

    @Test
    fun `sans historique - charge de départ, aucun bump`() {
        val t = targetForExercise(loadEx, emptyList(), true)
        assertEquals(20.0, t.weightKg, 0.0)
        assertEquals(0.0, t.bump, 0.0)
        assertNull(t.lastWeightKg)
        assertEquals(10, t.reps)
    }

    @Test
    fun `progression désactivée - continuité sur la dernière charge, aucun bump`() {
        val hist = listOf(point(maxWeightKg = 24.0, difficulty = Difficulty.FACILE))
        val t = targetForExercise(loadEx, hist, false)
        assertEquals(24.0, t.weightKg, 0.0) // dernière charge, pas de montée
        assertEquals(0.0, t.bump, 0.0)
        assertEquals(24.0, t.lastWeightKg!!, 0.0)
    }

    @Test
    fun `exercice non progressable (rééducation) - jamais de bump même activé`() {
        val hist = listOf(point(maxWeightKg = 0.0, topReps = 10, difficulty = Difficulty.FACILE))
        val t = targetForExercise(rehabEx, hist, true)
        assertEquals(0.0, t.bump, 0.0)
    }

    @Test
    fun `activé + ressenti facile - +2,5 kg et reps repartent en bas de fourchette`() {
        val hist = listOf(point(maxWeightKg = 20.0, topReps = 12, difficulty = Difficulty.FACILE))
        val t = targetForExercise(loadEx, hist, true)
        assertEquals(22.5, t.weightKg, 0.0)
        assertEquals(loadEx.repsMin, t.reps) // double progression : reset au bas de la fourchette
        assertEquals(2.5, t.bump, 0.0)
        assertEquals(ProgressKind.LOAD, t.bumpKind)
        assertEquals(20.0, t.lastWeightKg!!, 0.0)
    }

    @Test
    fun `dernière séance non notée - aucune montée (silence différent de feu vert)`() {
        // Dernière séance sans ressenti, malgré un « facile » plus ancien.
        val hist = listOf(
            point(maxWeightKg = 20.0, topReps = 12, difficulty = Difficulty.FACILE),
            point(maxWeightKg = 20.0, topReps = 12, difficulty = null),
        )
        val t = targetForExercise(loadEx, hist, true)
        assertEquals(0.0, t.bump, 0.0)
        assertEquals(20.0, t.weightKg, 0.0)
        assertNull(t.bumpKind)
    }

    @Test
    fun `gainage - dernière séance non notée garde la durée enregistrée`() {
        val hist = listOf(point(maxWeightKg = 0.0, topReps = 33, difficulty = null))
        val t = targetForExercise(timedEx, hist, true)
        assertEquals(0.0, t.weightKg, 0.0)
        assertEquals(33, t.reps)
        assertNull(t.lastWeightKg)
        assertEquals(0.0, t.bump, 0.0)
    }

    @Test
    fun `activé + dernière dur - allègement de 2,5 kg`() {
        val hist = listOf(point(maxWeightKg = 25.0, difficulty = Difficulty.DUR))
        val t = targetForExercise(loadEx, hist, true)
        assertEquals(22.5, t.weightKg, 0.0)
        assertEquals(-2.5, t.bump, 0.0)
        assertEquals(ProgressKind.LOAD, t.bumpKind)
        assertEquals(10, t.reps) // pas de montée : milieu de fourchette
    }

    @Test
    fun `gainage chronométré - progresse les secondes, pas la charge`() {
        val hist = listOf(point(maxWeightKg = 0.0, topReps = 30, difficulty = Difficulty.FACILE))
        val t = targetForExercise(timedEx, hist, true)
        assertEquals(0.0, t.weightKg, 0.0)
        assertEquals(35, t.reps)
        assertEquals(5.0, t.bump, 0.0)
        assertEquals(ProgressKind.TIME, t.bumpKind)
    }

    @Test
    fun `gainage - plafonné à repsMax × 1,5`() {
        val hist = listOf(point(maxWeightKg = 0.0, topReps = 60, difficulty = Difficulty.FACILE))
        val t = targetForExercise(timedEx, hist, true) // repsMax 40 -> plafond 60
        assertEquals(60, t.reps)
        assertEquals(0.0, t.bump, 0.0)
    }

    // --- isoWeekKey --------------------------------------------------------

    @Test
    fun `deux jours d'une même semaine ISO donnent la même clé`() {
        // 2026-06-29 (lundi) et 2026-07-03 (vendredi) : même semaine ISO.
        assertEquals(isoWeekKey(LocalDate.of(2026, 6, 29)), isoWeekKey(LocalDate.of(2026, 7, 3)))
    }

    @Test
    fun `deux semaines différentes donnent des clés différentes`() {
        assertNotEquals(isoWeekKey(LocalDate.of(2026, 7, 3)), isoWeekKey(LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun `format 4 chiffres - W deux chiffres`() {
        assertTrue(Regex("^\\d{4}-W\\d{2}$").matches(isoWeekKey(LocalDate.of(2026, 1, 5))))
        assertEquals("2026-W02", isoWeekKey(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `la semaine ISO chevauchant le nouvel an appartient à l'année du jeudi`() {
        // 2027-01-01 est un vendredi : semaine 53 de 2026.
        assertEquals("2026-W53", isoWeekKey(LocalDate.of(2027, 1, 1)))
        // 2024-12-30 (lundi) : semaine 1 de 2025.
        assertEquals("2025-W01", isoWeekKey(LocalDate.of(2024, 12, 30)))
    }

    // --- Descriptions ------------------------------------------------------

    private val up = ProgressionChange("Goblet squat", ProgressKind.LOAD, from = 20.0, to = 22.5, direction = Direction.UP)
    private val down = ProgressionChange("Rowing", ProgressKind.LOAD, from = 20.0, to = 17.5, direction = Direction.DOWN)
    private val timed = ProgressionChange("Gainage planche", ProgressKind.TIME, from = 30.0, to = 35.0, direction = Direction.UP)

    @Test
    fun `describeChange formate charge (virgule décimale) et durée`() {
        assertEquals("Goblet squat 20 → 22,5 kg", describeChange(up))
        assertEquals("Gainage planche 30 → 35 s", describeChange(timed))
    }

    @Test
    fun `fmtKg - entier tel quel, sinon une décimale avec virgule`() {
        assertEquals("20", fmtKg(20.0))
        assertEquals("22,5", fmtKg(22.5))
        assertEquals("0", fmtKg(0.0))
    }

    @Test
    fun `changeSummaryLine compte montées et allègements`() {
        assertEquals("2 exercices renforcés · 1 allégé", changeSummaryLine(listOf(up, timed, down)))
        assertEquals("1 exercice renforcé", changeSummaryLine(listOf(up)))
        assertEquals("2 allégés", changeSummaryLine(listOf(down, down)))
        assertEquals("", changeSummaryLine(emptyList()))
    }

    @Test
    fun `notificationContent choisit le titre selon le sens et liste le détail`() {
        assertEquals("Ton programme monte d’un cran", notificationContent(listOf(up)).title)
        assertEquals("On lève le pied cette semaine", notificationContent(listOf(down)).title)
        assertEquals("Ton programme évolue cette semaine", notificationContent(listOf(up, down)).title)
        assertEquals("Goblet squat 20 → 22,5 kg.", notificationContent(listOf(up)).body)
    }

    @Test
    fun `notificationContent tronque à trois changements avec le reste en +N`() {
        val body = notificationContent(listOf(up, timed, down, up, up)).body
        assertEquals("Goblet squat 20 → 22,5 kg, Gainage planche 30 → 35 s, Rowing 20 → 17,5 kg, +2.", body)
    }

    // --- isChange ----------------------------------------------------------

    @Test
    fun `isChange accepte une entrée bien formée et rejette le reste`() {
        val ok = mapOf("exercise" to "Goblet squat", "kind" to "load", "from" to 20, "to" to 22.5, "direction" to "up")
        assertTrue(isChange(ok))
        assertEquals(up, progressionChangeFrom(ok))
        assertFalse(isChange(null))
        assertFalse(isChange("x"))
        assertFalse(isChange(ok - "exercise"))
        assertFalse(isChange(ok + ("kind" to "reps")))
        assertFalse(isChange(ok + ("from" to "20")))
        assertFalse(isChange(ok + ("direction" to "none")))
    }
}
