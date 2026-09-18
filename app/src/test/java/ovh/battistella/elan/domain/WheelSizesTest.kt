// Tests sanity sur le barème des circonférences de roue.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelSizesTest {
    @Test
    fun `contient au moins quelques presets courants`() {
        assertTrue(WHEEL_SIZES.size >= 5)
        assertEquals(11, WHEEL_SIZES.size)
    }

    @Test
    fun `toutes les valeurs sont dans une plage plausible (1800 – 2400 mm)`() {
        for (w in WHEEL_SIZES) {
            assertTrue(w.mm > 1800)
            assertTrue(w.mm < 2400)
        }
    }

    @Test
    fun `chaque preset a un label non vide`() {
        for (w in WHEEL_SIZES) assertTrue(w.label.isNotEmpty())
    }

    @Test
    fun `inclut le preset route classique 700×25c (2105 mm)`() {
        val route = WHEEL_SIZES.firstOrNull { it.label == "700×25c" }
        assertNotNull(route)
        assertEquals(2105, route?.mm)
    }

    @Test
    fun `les labels sont uniques (pas de doublon dans la liste)`() {
        val labels = WHEEL_SIZES.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `matchWheelSize retrouve un preset par sa circonférence exacte`() {
        assertEquals("700×25c", matchWheelSize(2105)?.label)
    }

    @Test
    fun `matchWheelSize retourne null pour une valeur non listée`() {
        assertNull(matchWheelSize(1234))
    }

    @Test
    fun `matchWheelSize - comparaison stricte (un mm de différence ne matche pas)`() {
        assertNull(matchWheelSize(2104))
    }
}
