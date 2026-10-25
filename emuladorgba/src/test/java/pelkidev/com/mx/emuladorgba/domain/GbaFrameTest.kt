package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun stripes_alternateEveryEightRows() {
        val frame = stripeFrame()

        assertEquals(0xFF000000.toInt(), frame.pixel(0, 0))
        assertEquals(0xFF000000.toInt(), frame.pixel(GbaFrame.WIDTH - 1, 7))
        assertEquals(0xFFFFFFFF.toInt(), frame.pixel(0, 8))
        assertEquals(0xFFFFFFFF.toInt(), frame.pixel(120, 15))
        assertEquals(0xFF000000.toInt(), frame.pixel(0, 16))
    }

    @Test
    fun title_isDrawnOnTheFrameInsteadOfStripes() {
        val frame = titleFrame("A")
        val scale = 3
        val textWidth = 5 * scale
        val originX = (GbaFrame.WIDTH - textWidth) / 2
        val originY = (GbaFrame.HEIGHT - 7 * scale) / 2

        assertEquals(0xFF000000.toInt(), frame.pixel(0, 0))
        assertEquals(0xFF000000.toInt(), frame.pixel(originX, originY))
        assertEquals(0xFFFFFFFF.toInt(), frame.pixel(originX + scale, originY))
        assertFalse(frame.pixels.contentEquals(stripeFrame().pixels))
        assertFalse(frame.pixels.contentEquals(titleFrame("B").pixels))
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
