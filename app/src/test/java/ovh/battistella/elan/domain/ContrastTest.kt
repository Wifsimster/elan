// Tests du choix d'encre lisible (Contrast.kt).
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val blanc = "#FFFFFF"
    private val encreSombre = "#0B0E13" // `OnBright` du thème
    private val marche = "#A3E635" // teinte lime — la plus claire du thème
    private val velo = "#22D3C5"
    private val muscu = "#A78BFA"
    private val accent = "#5B7CFF"
    private val encres = listOf(encreSombre, blanc)

    @Test
    fun `relativeLuminance va de 0 (noir) à 1 (blanc)`() {
        assertEquals(0.0, relativeLuminance("#000000"), 1e-5)
        assertEquals(1.0, relativeLuminance(blanc), 1e-5)
    }

    @Test
    fun `relativeLuminance accepte la forme courte et ignore le dièse`() {
        assertEquals(relativeLuminance(blanc), relativeLuminance("#fff"), 1e-5)
        assertEquals(relativeLuminance(blanc), relativeLuminance("FFFFFF"), 1e-5)
        assertEquals(relativeLuminance(blanc), relativeLuminance("  #ffffff "), 1e-5)
    }

    @Test
    fun `relativeLuminance retombe sur 0 pour une chaîne invalide plutôt que NaN`() {
        assertEquals(0.0, relativeLuminance("pas une couleur"), 0.0)
        assertEquals(0.0, relativeLuminance("#12345"), 0.0)
        assertEquals(0.0, relativeLuminance("#GGGGGG"), 0.0)
    }

    @Test
    fun `contrastRatio vaut 21 entre noir et blanc, 1 pour une couleur avec elle-même`() {
        assertEquals(21.0, contrastRatio("#000000", blanc), 1e-2)
        assertEquals(1.0, contrastRatio(marche, marche), 1e-5)
    }

    @Test
    fun `contrastRatio est symétrique`() {
        assertEquals(contrastRatio(blanc, marche), contrastRatio(marche, blanc), 1e-5)
    }

    @Test
    fun `bestInk pose une encre sombre sur les teintes claires du thème`() {
        assertEquals(encreSombre, bestInk(marche, encres))
        assertEquals(encreSombre, bestInk(velo, encres))
    }

    @Test
    fun `bestInk garde le blanc sur un fond sombre`() {
        assertEquals(blanc, bestInk("#000000", encres))
        assertEquals(blanc, bestInk("#14181F", encres)) // surface de carte
    }

    @Test
    fun `bestInk retient l'encre sombre sur TOUTES les teintes d'activité du thème`() {
        // Résultat mesuré, pas choisi : même sur le violet muscu ou le bleu accent,
        // que l'app peignait en blanc, le sombre contraste deux fois mieux.
        for (teinte in listOf(marche, velo, muscu, accent)) {
            assertEquals(encreSombre, bestInk(teinte, encres))
            assertTrue(contrastRatio(teinte, encreSombre) > contrastRatio(teinte, blanc))
        }
    }

    @Test
    fun `le choix retenu passe le seuil AA sur les teintes du thème`() {
        for (teinte in listOf(marche, velo, muscu, accent, "#38BDF8", "#FF5C7A")) {
            assertTrue(contrastRatio(teinte, bestInk(teinte, encres)) >= 4.5)
        }
    }

    @Test
    fun `bestInk choisit toujours la meilleure des candidates, même sans seuil atteint`() {
        // Un gris moyen : aucune encre ne passe AA, on prend quand même la meilleure.
        val gris = "#767676"
        val choisie = bestInk(gris, encres)
        val autre = if (choisie == blanc) encreSombre else blanc
        assertTrue(contrastRatio(gris, choisie) >= contrastRatio(gris, autre))
    }

    @Test
    fun `bestInk - à égalité, la première candidate l'emporte`() {
        assertEquals("#000000", bestInk("#808080", listOf("#000000", "#000")))
    }
}
