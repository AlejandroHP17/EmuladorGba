package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertEquals
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
    fun emptyOrUnreadableTitle_hasNoTitle() {
        assertNull(romTitle(headerWithTitle("            ")))
        assertNull(romTitle(headerWithTitle("MARIO\u0000KART  ")))
        assertNull(romTitle(ByteArray(0)))
        assertNull(romTitle(ByteArray(0xA0 + 12 - 1)))
    }

    private fun headerWithTitle(title: String): ByteArray {
        val rom = ByteArray(0xA0 + 12)
        title.toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        return rom
    }
}
