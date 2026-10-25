package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RomTitleTest {

    @Test
    fun title_isTheTwelveBytesStoredInTheHeader() {
        val rom = headerWithTitle("MARIO KART  ")

        assertEquals("MARIO KART", romTitle(rom))
    }

    @Test
    fun anotherHeader_hasItsOwnTitle() {
        val rom = headerWithTitle("TETRIS      ")

        assertEquals("TETRIS", romTitle(rom))
    }

    @Test
    fun trailingSpaces_areNotPartOfTheTitle() {
        val rom = headerWithTitle("ZELDA       ")

        assertEquals("ZELDA", romTitle(rom))
    }

    @Test
    fun trailingNulls_areNotPartOfTheTitle() {
        val rom = headerWithTitle("MARIO KART\u0000\u0000")

        assertEquals("MARIO KART", romTitle(rom))
    }

    @Test
    fun emptyOrUnreadableTitle_hasNoTitle() {
        assertNull(romTitle(headerWithTitle("            ")))
        assertNull(romTitle(headerWithTitle("MARIO\u0000KART  ")))
        assertNull(romTitle(ByteArray(0)))
        assertNull(romTitle(ByteArray(0xA0 + 12 - 1)))
    }

    @Test
    fun readableTitle_isDrawnInsteadOfTheFileName() {
        val rom = headerWithTitle("MARIO KART  ")

        assertArrayEquals(titleFrame("MARIO KART").pixels, romFrame(rom, "otro.gba").pixels)
    }

    @Test
    fun emptyOrUnreadableTitle_drawsTheFileName() {
        assertArrayEquals(
            titleFrame("OTRO.GBA").pixels,
            romFrame(headerWithTitle("            "), "otro.gba").pixels,
        )
        assertArrayEquals(
            titleFrame("NOTAS.TXT").pixels,
            romFrame(headerWithTitle("MARIO\u0000KART  "), "notas.txt").pixels,
        )
        assertArrayEquals(
            titleFrame("JUEGO.GBA").pixels,
            romFrame(ByteArray(0), "juego.gba").pixels,
        )
        assertFalse(titleFrame(".").pixels.contentEquals(titleFrame("?").pixels))
    }

    private fun headerWithTitle(title: String): ByteArray {
        val rom = ByteArray(0xA0 + 12)
        title.toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        return rom
    }
}
