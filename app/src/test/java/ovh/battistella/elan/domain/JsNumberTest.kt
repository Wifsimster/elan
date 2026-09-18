// Tests de `Double.toJsString()` : vecteurs alignés sur `Number#toString()` de V8.
package ovh.battistella.elan.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class JsNumberTest {
    @Test
    fun `entiers sans point décimal`() {
        assertEquals("45", 45.0.toJsString())
        assertEquals("0", 0.0.toJsString())
        assertEquals("-12", (-12.0).toJsString())
        assertEquals("100", 100.0.toJsString())
    }

    @Test
    fun `plus courte représentation qui redonne le même double`() {
        assertEquals("48.8566", 48.8566.toJsString())
        assertEquals("2.3522", 2.3522.toJsString())
        assertEquals("0.1", 0.1.toJsString())
        assertEquals("0.30000000000000004", (0.1 + 0.2).toJsString())
        assertEquals("1.7976931348623157e+308", Double.MAX_VALUE.toJsString())
        assertEquals("5e-324", Double.MIN_VALUE.toJsString())
    }

    @Test
    fun `notation exponentielle à partir de 1e21`() {
        assertEquals("1e+21", 1e21.toJsString())
        assertEquals("123456789012345680000", 123456789012345680000.0.toJsString())
        assertEquals("1.5e+22", 1.5e22.toJsString())
    }

    @Test
    fun `notation exponentielle sous 1e-6`() {
        assertEquals("0.000001", 0.000001.toJsString())
        assertEquals("1e-7", 1e-7.toJsString())
        assertEquals("2.5e-7", 2.5e-7.toJsString())
        assertEquals("-2.5e-7", (-2.5e-7).toJsString())
    }

    @Test
    fun `zéro négatif et valeurs spéciales`() {
        assertEquals("0", (-0.0).toJsString())
        assertEquals("NaN", Double.NaN.toJsString())
        assertEquals("Infinity", Double.POSITIVE_INFINITY.toJsString())
        assertEquals("-Infinity", Double.NEGATIVE_INFINITY.toJsString())
    }

    @Test
    fun `toujours ré-analysable en la même valeur`() {
        val values = doubleArrayOf(48.8566, 3.14159, 1e21, 1e-7, 0.1 + 0.2, 123456.789, 6.02e23, 9007199254740993.0)
        for (v in values) assertEquals(v, v.toJsString().toDouble(), 0.0)
    }
}
