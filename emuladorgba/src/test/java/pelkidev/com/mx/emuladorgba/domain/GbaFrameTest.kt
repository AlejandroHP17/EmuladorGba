package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GbaFrameTest {

    @Test
    fun frame_measures240By160() {
        val frame = GbaFrame(IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT))

        assertEquals(240, GbaFrame.WIDTH)
        assertEquals(160, GbaFrame.HEIGHT)
        assertEquals(240 * 160, frame.pixels.size)
    }

    @Test
    fun pixel_isTheOneStoredAtThatPosition() {
        val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT)
        val color = 0xFF112233.toInt()
        pixels[1 * GbaFrame.WIDTH + 2] = color

        val frame = GbaFrame(pixels)

        assertEquals(color, frame.pixel(2, 1))
    }

    @Test
    fun otherSize_isNotAFrame() {
        try {
            GbaFrame(IntArray(10))
            fail("Un cuadro de otro tamaño no es válido")
        } catch (_: IllegalArgumentException) {
        }
    }
}
