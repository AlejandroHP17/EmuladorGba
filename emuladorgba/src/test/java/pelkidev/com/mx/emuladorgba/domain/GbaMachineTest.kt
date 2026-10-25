package pelkidev.com.mx.emuladorgba.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GbaMachineTest {

    @Test
    fun syntheticProgram_paintsAKnownPixel() {
        val machine = GbaMachine(paintRedAtOrigin())

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(BLACK, machine.frame().pixel(1, 0))
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, machine.frame().pixels.size)
    }

    @Test
    fun unknownInstruction_isSkippedAndTheFrameStays() {
        val machine = GbaMachine(paintThenUnknown())

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(RED, machine.frame().pixel(1, 0))
    }

    @Test
    fun unknownThumbInstruction_isSkippedAndTheFollowingStorePaints() {
        val arm = words(
            0xE3A00000.toInt(),
            0xE3A01406.toInt(),
            0xE3A0201F.toInt(),
            0xE28F3001.toInt(),
            0xE12FFF13.toInt(),
        )
        val machine = GbaMachine(arm + byteArrayOf(0x00, 0xDE.toByte(), 0x0A, 0x52))

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun jump_thenPaintsAKnownPixel() {
        val machine = GbaMachine(
            words(
                0xEA000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xE3A01406.toInt(),
                0xE3A0201F.toInt(),
                0xE1C120B0.toInt(),
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(BLACK, machine.frame().pixel(1, 0))
    }

    @Test
    fun load_paintsTheValueReadFromTheProgram() {
        val machine = GbaMachine(
            words(
                0xE59F2004.toInt(),
                0xE3A01406.toInt(),
                0xE1C120B0.toInt(),
                31,
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun thumbAdd_paintsTheComputedColor() {
        val arm = words(
            0xE3A00000.toInt(),
            0xE3A01406.toInt(),
            0xE28F3001.toInt(),
            0xE12FFF13.toInt(),
        )
        val machine = GbaMachine(arm + byteArrayOf(0x1E, 0x22, 0x01, 0x32, 0x0A, 0x52))

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun directCopy_paintsTheCopiedColor() {
        val machine = GbaMachine(
            words(
                0xE59F4014.toInt(),
                0xE59F0014.toInt(),
                0xE59F1014.toInt(),
                0xE59F2014.toInt(),
                0xE5840000.toInt(),
                0xE5841004.toInt(),
                0xE5842008.toInt(),
                0x040000D4,
                0x0800002C.toInt(),
                0x06000000,
                0x80000001.toInt(),
                0x001F,
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun biosCopy_paintsTheCopiedColor() {
        val machine = GbaMachine(
            words(
                0xE59F0008.toInt(),
                0xE59F1008.toInt(),
                0xE59F2008.toInt(),
                0xEF00000B.toInt(),
                0x0800001C.toInt(),
                0x06000000,
                1,
                0x001F,
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun biosExpand_paintsTheExpandedColor() {
        val machine = GbaMachine(
            words(
                0xE59F0004.toInt(),
                0xE59F1004.toInt(),
                0xEF000012.toInt(),
                0x08000014.toInt(),
                0x06000000,
                0x00000210,
                0x00001F00,
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun thumbStore_paintsAKnownPixel() {
        val arm = words(
            0xE3A00000.toInt(),
            0xE3A01406.toInt(),
            0xE3A0201F.toInt(),
            0xE28F3001.toInt(),
            0xE12FFF13.toInt(),
        )
        val machine = GbaMachine(arm + byteArrayOf(0x0A, 0x52))

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun imageWrite_appearsAtThatPixelOfTheFrame() {
        val machine = GbaMachine(
            words(
                0xE59F1004.toInt(),
                0xE3A0201F.toInt(),
                0xE1C120B0.toInt(),
                0x06000000 + (20 * GbaFrame.WIDTH + 10) * 2,
            ),
        )

        machine.step()

        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, machine.frame().pixels.size)
        assertEquals(RED, machine.frame().pixel(10, 20))
        assertEquals(BLACK, machine.frame().pixel(0, 0))
    }

    @Test
    fun writeOutsideTheImage_staysBlack() {
        val machine = GbaMachine(
            words(
                0xE3A01402.toInt(),
                0xE3A0201F.toInt(),
                0xE1C120B0.toInt(),
            ),
        )

        machine.step()

        assertEquals(BLACK, machine.frame().pixel(0, 0))
    }

    @Test
    fun verticalBlank_runsTheInstalledHandler() {
        val machine = GbaMachine(
            words(
                0xE3A00408.toInt(),
                0xE2800021.toInt(),
                0xE3A01403.toInt(),
                0xE2811C4F.toInt(),
                0xE2811080.toInt(),
                0xE5810000.toInt(),
                0xEF000005.toInt(),
                0xEAFFFFFE.toInt(),
            ) + byteArrayOf(
                0x01, 0x49,
                0x1F, 0x20,
                0x08, 0x80.toByte(),
                0x70, 0x47,
                0x00, 0x00, 0x00, 0x06,
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
    }

    @Test
    fun tiledBackground_paintsTheKnownColor() {
        val machine = GbaMachine(
            words(
                0xE3A00404.toInt(),
                0xE3A01C01.toInt(),
                0xE1C010B0.toInt(),
                0xE1C010B8.toInt(),
                0xE3A03405.toInt(),
                0xE3A0201F.toInt(),
                0xE1C320B2.toInt(),
                0xE3A04406.toInt(),
                0xE3A02001.toInt(),
                0xE1C420B0.toInt(),
            ),
        )

        machine.step()

        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, machine.frame().pixels.size)
        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(BLACK, machine.frame().pixel(1, 0))
    }

    @Test
    fun unknownInstruction_keepsTheTiledBackground() {
        val machine = GbaMachine(
            words(
                0xE3A00404.toInt(),
                0xE3A01C01.toInt(),
                0xE1C010B0.toInt(),
                0xE1C010B8.toInt(),
                0xE3A03405.toInt(),
                0xE3A0201F.toInt(),
                0xE1C320B2.toInt(),
                0xE3A04406.toInt(),
                0xE3A02001.toInt(),
                0xE1C420B0.toInt(),
                0xFFFFFFFF.toInt(),
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(BLACK, machine.frame().pixel(1, 0))
    }

    @Test
    fun laterBackgroundMemory_paintsTheKnownColor() {
        val machine = GbaMachine(
            words(
                0xE3A00404.toInt(),
                0xE3A01C01.toInt(),
                0xE1C010B0.toInt(),
                0xE3A01C1F.toInt(),
                0xE281100C.toInt(),
                0xE1C010B8.toInt(),
                0xE3A03405.toInt(),
                0xE2833E1E.toInt(),
                0xE3A0201F.toInt(),
                0xE1C320B2.toInt(),
                0xE3A04406.toInt(),
                0xE2844A0C.toInt(),
                0xE3A02001.toInt(),
                0xE1C420B0.toInt(),
                0xE3A04406.toInt(),
                0xE2844CF8.toInt(),
                0xE3A02CF0.toInt(),
                0xE1C420B0.toInt(),
            ),
        )

        machine.step()

        assertEquals(RED, machine.frame().pixel(0, 0))
        assertEquals(BLACK, machine.frame().pixel(1, 0))
    }

    @Test
    fun emptyRom_staysBlank() {
        val machine = GbaMachine(ByteArray(0))

        machine.step()

        assertEquals(BLACK, machine.frame().pixel(0, 0))
    }

    private fun paintRedAtOrigin(): ByteArray = words(
        0xE3A01406.toInt(), // mov r1, #0x06000000
        0xE3A0201F.toInt(), // mov r2, #31
        0xE1C120B0.toInt(), // strh r2, [r1]
    )

    private fun paintThenUnknown(): ByteArray = words(
        0xE3A01406.toInt(),
        0xE3A0201F.toInt(),
        0xE1C120B0.toInt(),
        0xFFFFFFFF.toInt(),
        0xE1C120B2.toInt(), // strh r2, [r1, #2]
    )

    private fun words(vararg instructions: Int): ByteArray {
        val bytes = ByteArray(instructions.size * 4)
        instructions.forEachIndexed { index, word ->
            bytes[index * 4] = word.toByte()
            bytes[index * 4 + 1] = (word ushr 8).toByte()
            bytes[index * 4 + 2] = (word ushr 16).toByte()
            bytes[index * 4 + 3] = (word ushr 24).toByte()
        }
        return bytes
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
