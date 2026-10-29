package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionAdviceTest {
    @Test
    fun `difficultyScore mappe chaque ressenti sur un score ordonnable, null sinon`() {
        assertEquals(1, difficultyScore(Difficulty.FACILE))
        assertEquals(2, difficultyScore(Difficulty.MOYEN))
        assertEquals(3, difficultyScore(Difficulty.DUR))
        assertNull(difficultyScore(null))
    }

    @Test
    fun `difficultyLabel libelle chaque ressenti en français`() {
        assertEquals("Facile", difficultyLabel(Difficulty.FACILE))
        assertEquals("Moyen", difficultyLabel(Difficulty.MOYEN))
        assertEquals("Dur", difficultyLabel(Difficulty.DUR))
    }

    @Test
    fun `aucune note exploitable - maintiens`() {
        assertEquals(ProgressionAdvice.MAINTIENS, suggestProgression(emptyList()))
        assertEquals(ProgressionAdvice.MAINTIENS, suggestProgression(listOf(null, null)))
    }

    @Test
    fun `deux séances faciles - augmente`() {
        assertEquals(ProgressionAdvice.AUGMENTE, suggestProgression(listOf(Difficulty.FACILE, Difficulty.FACILE)))
    }

    @Test
    fun `dernière facile sans dur récent - augmente`() {
        assertEquals(ProgressionAdvice.AUGMENTE, suggestProgression(listOf(Difficulty.MOYEN, Difficulty.FACILE)))
    }

    @Test
    fun `dernière dure - réduis (priorité sur la moyenne)`() {
        assertEquals(ProgressionAdvice.REDUIS, suggestProgression(listOf(Difficulty.FACILE, Difficulty.DUR)))
    }

    @Test
    fun `moyen dominant - maintiens`() {
        assertEquals(
            ProgressionAdvice.MAINTIENS,
            suggestProgression(listOf(Difficulty.MOYEN, Difficulty.MOYEN, Difficulty.MOYEN)),
        )
    }

    @Test
    fun `deux séances dures - réduis`() {
        assertEquals(ProgressionAdvice.REDUIS, suggestProgression(listOf(Difficulty.DUR, Difficulty.DUR)))
    }

    @Test
    fun `ignore les séances non notées`() {
        assertEquals(
            ProgressionAdvice.AUGMENTE,
            suggestProgression(listOf(null, Difficulty.FACILE, null, Difficulty.FACILE)),
        )
    }

    @Test
    fun `respecte la fenêtre - un dur ancien sort du calcul`() {
        val recent = listOf(Difficulty.DUR, Difficulty.FACILE, Difficulty.FACILE)
        assertEquals(ProgressionAdvice.AUGMENTE, suggestProgression(recent, 2))
        assertEquals(ProgressionAdvice.MAINTIENS, suggestProgression(recent, 3))
    }

    @Test
    fun `fenêtre nulle - toutes les séances notées comptent (slice(-0) JS)`() {
        assertEquals(
            ProgressionAdvice.MAINTIENS,
            suggestProgression(listOf(Difficulty.DUR, Difficulty.FACILE, Difficulty.FACILE), 0),
        )
    }

    @Test
    fun `adviceLabel formule chaque conseil au tutoiement`() {
        assertEquals("Augmente les reps ou la charge", adviceLabel(ProgressionAdvice.AUGMENTE))
        assertEquals("Réduis la charge, soigne la technique", adviceLabel(ProgressionAdvice.REDUIS))
        assertEquals("Maintiens, charge bien calibrée", adviceLabel(ProgressionAdvice.MAINTIENS))
    }
}
