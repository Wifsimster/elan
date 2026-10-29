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
        assertEquals(40.0, epley1RM(30.0, 10), 1e-9)
        assertEquals(27.0, epley1RM(22.5, 6), 1e-9)
    }

    @Test
    fun `normalise des séries différentes sur une même échelle`() {
        assertEquals(28.0, epley1RM(20.0, 12), 1e-9)
        assertEquals(28.8, epley1RM(24.0, 6), 1e-9)
    }
}
