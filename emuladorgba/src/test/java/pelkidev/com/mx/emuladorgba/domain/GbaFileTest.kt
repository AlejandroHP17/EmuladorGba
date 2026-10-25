package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaFileTest {

    @Test
    fun nameEndingInGba_isAGbaFile() {
        assertTrue(isGbaFileName("Mario Kart Super Circuit.gba"))
    }

    @Test
    fun extension_ignoresCase() {
        assertTrue(isGbaFileName("juego.GBA"))
        assertTrue(isGbaFileName("juego.Gba"))
    }

    @Test
    fun otherExtension_isNotAGbaFile() {
        assertFalse(isGbaFileName("notas.txt"))
        assertFalse(isGbaFileName("juego.gb"))
        assertFalse(isGbaFileName("juego.gba.txt"))
    }

    @Test
    fun blankName_isNotAGbaFile() {
        assertFalse(isGbaFileName("   "))
    }
}
