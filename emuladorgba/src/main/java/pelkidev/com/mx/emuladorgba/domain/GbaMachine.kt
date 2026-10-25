package pelkidev.com.mx.emuladorgba.domain

private const val ROM_BASE = 0x08000000
private const val VRAM_BASE = 0x06000000
private const val INSTRUCTIONS_PER_STEP = 32
private const val BLACK = 0xFF000000.toInt()

class GbaMachine(private val rom: ByteArray) {
    private val registers = IntArray(16)
    private val vram = ByteArray(GbaFrame.WIDTH * GbaFrame.HEIGHT * 2)
    private var pc = ROM_BASE
    private var halted = false

    fun step() {
        repeat(INSTRUCTIONS_PER_STEP) {
            if (halted) return
            executeOne()
        }
    }

    fun frame(): GbaFrame {
        val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { index ->
            val lo = vram[index * 2].toInt() and 0xFF
            val hi = vram[index * 2 + 1].toInt() and 0xFF
            rgb555(lo or (hi shl 8))
        }
        return GbaFrame(pixels)
    }

    private fun executeOne() {
        val word = fetch(pc)
        when {
            isMovImmediate(word) -> {
                val rd = (word ushr 12) and 0xF
                val value = armImmediate(word)
                if (rd == 15) {
                    pc = value
                } else {
                    registers[rd] = value
                    pc += 4
                }
            }
            isStrhImmediate(word) -> {
                val rn = (word ushr 16) and 0xF
                val rd = (word ushr 12) and 0xF
                val offset = (((word ushr 8) and 0xF) shl 4) or (word and 0xF)
                val base = if (rn == 15) pc else registers[rn]
                writeHalf(base + offset, registers[rd])
                pc += 4
            }
            else -> halted = true
        }
    }

    private fun fetch(address: Int): Int {
        val offset = address - ROM_BASE
        if (offset < 0 || offset + 3 >= rom.size) return 0
        return (rom[offset].toInt() and 0xFF) or
            ((rom[offset + 1].toInt() and 0xFF) shl 8) or
            ((rom[offset + 2].toInt() and 0xFF) shl 16) or
            ((rom[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeHalf(address: Int, value: Int) {
        val offset = address - VRAM_BASE
        if (offset < 0 || offset + 1 >= vram.size || offset % 2 != 0) return
        vram[offset] = value.toByte()
        vram[offset + 1] = (value ushr 8).toByte()
    }
}

private fun isMovImmediate(word: Int): Boolean {
    if (word ushr 28 != 0xE) return false
    if ((word ushr 25) and 7 != 1) return false
    return (word ushr 21) and 0xF == 0xD
}

private fun isStrhImmediate(word: Int): Boolean =
    (word and 0x0FF000F0.toInt()) == 0x01C000B0

private fun armImmediate(word: Int): Int {
    val rotate = ((word ushr 8) and 0xF) * 2
    return Integer.rotateRight(word and 0xFF, rotate)
}

private fun rgb555(color: Int): Int {
    if (color == 0) return BLACK
    val red = (color and 0x1F) * 255 / 31
    val green = ((color ushr 5) and 0x1F) * 255 / 31
    val blue = ((color ushr 10) and 0x1F) * 255 / 31
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
