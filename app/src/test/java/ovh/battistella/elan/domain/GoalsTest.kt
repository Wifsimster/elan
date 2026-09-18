// Tests de la logique pure des objectifs d'entraînement (Goals.kt) : bornes de
// période, calcul d'avancement, libellés, parsing robuste et opérations de
// liste. Robolectric uniquement pour `org.json` (parseGoals / serializeGoals).
// Dates construites en heure locale (fuseau par défaut du système, comme
// `startOfWeekMs`) pour rester indépendant du fuseau de la CI.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class GoalsTest {
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun goal(
        id: String = "g1",
        metric: GoalMetric = GoalMetric.SESSIONS,
        period: GoalPeriod = GoalPeriod.WEEK,
        target: Double = 3.0,
        activity: GoalActivity = GoalActivity.ALL,
    ) = Goal(id, metric, period, target, activity)

    private fun localMs(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)

    // --- periodRange ---------------------------------------------------------

    @Test
    fun `semaine - lundi 00h00 vers lundi suivant (7 jours)`() {
        val now = localMs(2025, 6, 11, 14, 30) // mercredi 11 juin
        val (fromMs, toMs) = periodRange(GoalPeriod.WEEK, now)
        val from = local(fromMs)
        assertEquals(1, from.dayOfWeek.value) // lundi
        assertEquals(9, from.dayOfMonth)
        assertEquals(0, from.hour)
        assertEquals(7 * 86_400_000L, toMs - fromMs)
    }

    @Test
    fun `semaine - la borne de fin est re-calée sur le lundi suivant à minuit local`() {
        // Semaine du passage à l'heure d'été (30 mars 2025 en Europe) : avec un
        // +168 h fixe la borne dériverait d'une heure ; on exige lundi 00:00 local.
        val now = localMs(2025, 3, 26, 10, 0) // mercredi
        val (fromMs, toMs) = periodRange(GoalPeriod.WEEK, now)
        val from = local(fromMs)
        val to = local(toMs)
        assertEquals(1, to.dayOfWeek.value)
        assertEquals(0, to.hour)
        assertEquals(0, to.minute)
        assertEquals(from.toLocalDate().plusDays(7), to.toLocalDate())
        assertEquals(31, to.dayOfMonth)
    }

    @Test
    fun `mois - 1er du mois vers 1er du mois suivant`() {
        val now = localMs(2025, 6, 11, 14, 30) // juin
        val (fromMs, toMs) = periodRange(GoalPeriod.MONTH, now)
        val from = local(fromMs)
        val to = local(toMs)
        assertEquals(1, from.dayOfMonth)
        assertEquals(6, from.monthValue) // juin
        assertEquals(0, from.hour)
        assertEquals(1, to.dayOfMonth)
        assertEquals(7, to.monthValue) // juillet
    }

    @Test
    fun `mois - décembre déborde sur janvier de l'année suivante`() {
        val now = localMs(2025, 12, 20) // décembre 2025
        val (_, toMs) = periodRange(GoalPeriod.MONTH, now)
        val to = local(toMs)
        assertEquals(2026, to.year)
        assertEquals(1, to.monthValue) // janvier
    }

    // --- computeProgress -----------------------------------------------------

    @Test
    fun `avancement partiel - ratio borné, non atteint`() {
        val p = computeProgress(goal(target = 4.0), 1.0)
        assertEquals(0.25, p.ratio, 1e-9)
        assertFalse(p.done)
        assertEquals(1.0, p.value, 0.0)
        assertEquals(4.0, p.target, 0.0)
    }

    @Test
    fun `atteint à exactement la cible`() {
        val p = computeProgress(goal(target = 3.0), 3.0)
        assertTrue(p.done)
        assertEquals(1.0, p.ratio, 0.0)
    }

    @Test
    fun `dépassement - ratio plafonné à 1, done vrai`() {
        val p = computeProgress(goal(target = 3.0), 5.0)
        assertEquals(1.0, p.ratio, 0.0)
        assertTrue(p.done)
    }

    @Test
    fun `cible nulle - ratio 0, jamais atteint (pas de division par zéro)`() {
        val p = computeProgress(goal(target = 0.0), 2.0)
        assertEquals(0.0, p.ratio, 0.0)
        assertFalse(p.done)
    }

    // --- describeGoal / formatGoalValue --------------------------------------

    @Test
    fun `libellés selon métrique et période`() {
        assertEquals(
            "3 sorties vélo / semaine",
            describeGoal(goal(metric = GoalMetric.SESSIONS, activity = GoalActivity.VELO, target = 3.0)),
        )
        assertEquals(
            "8 séances muscu / mois",
            describeGoal(goal(metric = GoalMetric.SESSIONS, activity = GoalActivity.MUSCU, period = GoalPeriod.MONTH, target = 8.0)),
        )
        assertEquals("100 km / mois", describeGoal(goal(metric = GoalMetric.DISTANCE, period = GoalPeriod.MONTH, target = 100.0)))
        assertEquals(
            "5000 kg soulevés / mois",
            describeGoal(goal(metric = GoalMetric.TONNAGE, period = GoalPeriod.MONTH, target = 5000.0)),
        )
        assertEquals("3 séances / semaine", describeGoal(goal()))
    }

    @Test
    fun `valeur réalisée formatée avec unité`() {
        assertEquals("2", formatGoalValue(goal(metric = GoalMetric.SESSIONS), 2.0))
        assertEquals("42.5 km", formatGoalValue(goal(metric = GoalMetric.DISTANCE), 42.5))
        assertEquals("5123 kg", formatGoalValue(goal(metric = GoalMetric.TONNAGE), 5123.4))
    }

    // --- parseGoals (robuste) / serializeGoals -------------------------------

    @Test
    fun `valeur absente ou JSON corrompu - liste vide`() {
        assertEquals(emptyList<Goal>(), parseGoals(null))
        assertEquals(emptyList<Goal>(), parseGoals(""))
        assertEquals(emptyList<Goal>(), parseGoals("not json"))
        assertEquals(emptyList<Goal>(), parseGoals("""{"not":"array"}"""))
    }

    @Test
    fun `ignore les entrées invalides et conserve les valides`() {
        val raw = """[
            {"id":"a","metric":"sessions","period":"week","target":3,"activity":"velo"},
            {"id":"b","metric":"bogus","period":"week","target":3,"activity":"all"},
            {"id":"c","metric":"distance","period":"year","target":50,"activity":"all"},
            {"id":"d","metric":"tonnage","period":"month","target":0,"activity":"all"},
            {"id":"e","metric":"distance","period":"month","target":100}
        ]"""
        val goals = parseGoals(raw)
        assertEquals(listOf("a", "e"), goals.map { it.id })
        assertEquals(GoalActivity.ALL, goals[1].activity)
        assertEquals(100.0, goals[1].target, 0.0)
    }

    @Test
    fun `régénère un id absent et rejette une cible non numérique`() {
        val raw = """[
            {"metric":"sessions","period":"week","target":"3"},
            {"id":"","metric":"sessions","period":"week","target":2},
            {"id":"z","metric":"sessions","period":"week","target":"abc"}
        ]"""
        val goals = parseGoals(raw)
        assertEquals(2, goals.size)
        assertTrue(goals[0].id.startsWith("g"))
        assertTrue(goals[1].id.startsWith("g"))
        assertNotEquals(goals[0].id, goals[1].id)
        assertEquals(3.0, goals[0].target, 0.0)
    }

    @Test
    fun `round-trip serialize vers parse`() {
        val goals = listOf(
            goal(id = "x", metric = GoalMetric.DISTANCE, period = GoalPeriod.MONTH, target = 120.0, activity = GoalActivity.ALL),
            goal(id = "y", metric = GoalMetric.TONNAGE, period = GoalPeriod.WEEK, target = 2500.5, activity = GoalActivity.MUSCU),
        )
        assertEquals(goals, parseGoals(serializeGoals(goals)))
    }

    // --- upsertGoal / removeGoal / makeGoal ----------------------------------

    @Test
    fun `ajoute un nouvel objectif`() {
        assertEquals(1, upsertGoal(emptyList(), goal(id = "g1")).size)
    }

    @Test
    fun `remplace l'objectif de même id`() {
        val next = upsertGoal(listOf(goal(id = "g1", target = 3.0)), goal(id = "g1", target = 5.0))
        assertEquals(1, next.size)
        assertEquals(5.0, next[0].target, 0.0)
    }

    @Test
    fun `supprime par id`() {
        val next = removeGoal(listOf(goal(id = "g1"), goal(id = "g2")), "g1")
        assertEquals(listOf("g2"), next.map { it.id })
    }

    @Test
    fun `makeGoal attribue un id unique`() {
        val a = makeGoal(GoalMetric.SESSIONS, GoalPeriod.WEEK, 3.0, GoalActivity.ALL)
        val b = makeGoal(GoalMetric.SESSIONS, GoalPeriod.WEEK, 3.0, GoalActivity.ALL)
        assertNotEquals(a.id, b.id)
        assertTrue(a.id.isNotEmpty())
        assertTrue(a.id.startsWith("g"))
    }

    // --- libellés des nouvelles activités ------------------------------------

    @Test
    fun `nomme la course et la marche dans le libellé d'objectif`() {
        assertTrue(
            describeGoal(Goal("a", GoalMetric.SESSIONS, GoalPeriod.WEEK, 3.0, GoalActivity.COURSE))
                .contains("sorties course à pied"),
        )
        assertTrue(
            describeGoal(Goal("b", GoalMetric.SESSIONS, GoalPeriod.MONTH, 8.0, GoalActivity.MARCHE))
                .contains("sorties marche"),
        )
    }

    @Test
    fun `accepte course et marche à la relecture des réglages`() {
        val raw = """[
            {"id":"a","metric":"distance","period":"month","target":50,"activity":"course"},
            {"id":"b","metric":"sessions","period":"week","target":2,"activity":"marche"}
        ]"""
        assertEquals(2, parseGoals(raw).size)
        assertEquals(GoalActivity.COURSE, parseGoals(raw)[0].activity)
    }

    @Test
    fun `ramène une activité inconnue sur toutes sans perdre l'objectif`() {
        // Lecture défensive : un réglage restauré d'une autre version ne doit pas
        // disparaître silencieusement — il retombe sur le comptage le plus large.
        val raw = """[{"id":"c","metric":"sessions","period":"week","target":2,"activity":"natation"}]"""
        assertEquals(1, parseGoals(raw).size)
        assertEquals(GoalActivity.ALL, parseGoals(raw)[0].activity)
    }
}
