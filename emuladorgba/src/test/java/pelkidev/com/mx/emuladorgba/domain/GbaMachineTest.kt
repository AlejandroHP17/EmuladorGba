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
    fun unknownInstruction_keepsTheLastFrame() {
        val machine = GbaMachine(paintThenUnknown())

        machine.step()
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
