// Tests de l'estimation du 1RM (Strength.kt), formule d'Epley.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StrengthTest {
    @Test
    fun `série sans charge ou invalide - 0`() {
        assertEquals(0.0, epley1RM(0.0, 10), 0.0)
        assertEquals(0.0, epley1RM(-5.0, 10), 0.0)
        assertEquals(0.0, epley1RM(20.0, 0), 0.0)
        assertEquals(0.0, epley1RM(20.0, -1), 0.0)
    }

    @Test
    fun `une seule répétition - la charge elle-même`() {
        assertEquals(60.0, epley1RM(60.0, 1), 0.0)
    }

    @Test
    fun `formule d'Epley - poids × (1 + reps ÷ 30)`() {
        assertEquals(40.0, epley1RM(30.0, 10), 1e-9) // 30 × (1 + 10/30)
        assertEquals(27.0, epley1RM(22.5, 6), 1e-9) // 22,5 × 1,2
    }

    @Test
    fun `normalise des séries différentes sur une même échelle`() {
        // 20 kg × 12 reps et 24 kg × 6 reps donnent des 1RM comparables.
        assertEquals(28.0, epley1RM(20.0, 12), 1e-9)
        assertEquals(28.8, epley1RM(24.0, 6), 1e-9)
    }
}
