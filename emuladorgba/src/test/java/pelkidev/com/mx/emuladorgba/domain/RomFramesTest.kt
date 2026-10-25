package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RomFramesTest {

    @Test
    fun steps_alternateTitleAndHeaderGraphic() {
        val frames = romFrames(header("TETRIS      ", logoFirstByte = 0x80), "otro.gba")

        assertArrayEquals(titleFrame("TETRIS").pixels, frames.frameAt(0).pixels)
        assertArrayEquals(frames.graphic.pixels, frames.frameAt(1).pixels)
        assertArrayEquals(frames.frameAt(0).pixels, frames.frameAt(2).pixels)
        assertFalse(frames.frameAt(0).pixels.contentEquals(frames.frameAt(1).pixels))
    }

    @Test
    fun headerGraphic_drawsTheFirstBitOfTheLogo() {
        val graphic = headerGraphicFrame(header("TETRIS      ", logoFirstByte = 0x80))
        val originX = (GbaFrame.WIDTH - 13 * 8 * 2) / 2
        val originY = (GbaFrame.HEIGHT - 12 * 2) / 2

        assertEquals(0xFFFFFFFF.toInt(), graphic.pixel(originX, originY))
        assertEquals(0xFF000000.toInt(), graphic.pixel(originX + 2, originY))
    }

    @Test
    fun anotherTitle_changesOnlyTheTitleFrame() {
        val logo = 0x80
        val first = romFrames(header("TETRIS      ", logo), "otro.gba")
        val second = romFrames(header("ZELDA       ", logo), "otro.gba")

        assertFalse(first.frameAt(0).pixels.contentEquals(second.frameAt(0).pixels))
        assertArrayEquals(first.graphic.pixels, second.graphic.pixels)
    }

    private fun header(title: String, logoFirstByte: Int): ByteArray {
        val rom = ByteArray(0xA0 + 12)
        rom[0x04] = logoFirstByte.toByte()
        title.toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        return rom
    }
}
