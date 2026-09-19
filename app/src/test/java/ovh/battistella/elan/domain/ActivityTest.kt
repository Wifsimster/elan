// Tests des métadonnées d'activité : la table qui décide partout ailleurs si
// une séance a un tracé GPS et si l'effort se lit en allure.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTest {
    @Test
    fun `ACTIVITY_META couvre exactement les types listés, sans doublon`() {
        assertEquals(listOf("course", "marche", "muscu", "velo"), ACTIVITY_TYPES.map { it.key }.sorted())
        assertEquals(ACTIVITY_TYPES.size, ACTIVITY_TYPES.toSet().size)
        assertEquals(ACTIVITY_TYPES.toSet(), ACTIVITY_META.keys)
        assertEquals(ActivityType.entries.toSet(), ACTIVITY_META.keys)
    }

    @Test
    fun `donne à chaque activité un libellé, un libellé court, une icône et une teinte`() {
        for (type in ACTIVITY_TYPES) {
            val meta = ACTIVITY_META.getValue(type)
            assertTrue(meta.label.isNotEmpty())
            assertTrue(meta.shortLabel.isNotEmpty())
            assertTrue(meta.icon.isNotEmpty())
            assertTrue(meta.colorKey.isNotEmpty())
        }
    }

    @Test
    fun `donne une teinte distincte à chaque activité`() {
        val keys = ACTIVITY_TYPES.map { ACTIVITY_META.getValue(it).colorKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `isGpsActivity vrai pour le vélo, la course et la marche, faux en musculation`() {
        assertTrue(isGpsActivity(ActivityType.VELO))
        assertTrue(isGpsActivity(ActivityType.COURSE))
        assertTrue(isGpsActivity(ActivityType.MARCHE))
        assertFalse(isGpsActivity(ActivityType.MUSCU))
    }

    @Test
    fun `usesPace allure à pied, vitesse à vélo`() {
        assertTrue(usesPace(ActivityType.COURSE))
        assertTrue(usesPace(ActivityType.MARCHE))
        assertFalse(usesPace(ActivityType.VELO))
        assertFalse(usesPace(ActivityType.MUSCU))
    }

    @Test
    fun `usesPace ne concerne que des activités tracées`() {
        for (type in ACTIVITY_TYPES) {
            if (usesPace(type)) assertTrue(isGpsActivity(type))
        }
    }

    @Test
    fun `toActivityType accepte les types connus`() {
        for (type in ACTIVITY_TYPES) assertEquals(type, toActivityType(type.key))
    }

    @Test
    fun `toActivityType retombe sur le vélo pour une valeur inconnue ou absente`() {
        assertEquals(ActivityType.VELO, toActivityType(null))
        assertEquals(ActivityType.VELO, toActivityType("natation"))
        assertEquals(ActivityType.VELO, toActivityType(42))
        // Une clé héritée d'Object.prototype ne doit pas passer pour un type.
        assertEquals(ActivityType.VELO, toActivityType("toString"))
    }

    @Test
    fun `toActivityType honore le repli explicite`() {
        assertEquals(ActivityType.COURSE, toActivityType("inconnu", ActivityType.COURSE))
    }

    @Test
    fun `fromKey résout les clés stockées et rejette le reste`() {
        assertEquals(ActivityType.MARCHE, ActivityType.fromKey("marche"))
        assertEquals(null, ActivityType.fromKey("MARCHE"))
        assertEquals(null, ActivityType.fromKey(null))
        assertEquals(TrainingGoal.PERTE_POIDS, TrainingGoal.fromKey("perte-poids"))
        assertEquals(Difficulty.DUR, Difficulty.fromKey("dur"))
        assertEquals(Sex.F, Sex.fromKey("f"))
    }
}
