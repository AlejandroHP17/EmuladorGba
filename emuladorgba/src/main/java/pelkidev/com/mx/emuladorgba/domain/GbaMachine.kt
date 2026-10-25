package pelkidev.com.mx.emuladorgba.domain

private const val EWRAM_BASE = 0x02000000
private const val IWRAM_BASE = 0x03000000
private const val VRAM_BASE = 0x06000000
private const val ROM_BASE = 0x08000000
private const val INSTRUCTIONS_PER_STEP = 50_000
private const val BLACK = 0xFF000000.toInt()

class GbaMachine(private val rom: ByteArray) {
    private val registers = IntArray(16)
    private val ewram = ByteArray(256 * 1024)
    private val iwram = ByteArray(32 * 1024)
    private val vram = ByteArray(GbaFrame.WIDTH * GbaFrame.HEIGHT * 2)
    private var pc = ROM_BASE
    private var thumb = false

    fun step() {
        repeat(INSTRUCTIONS_PER_STEP) {
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
        if (thumb) executeThumb() else executeArm()
    }

    private fun executeArm() {
        val word = readWord(pc)
        when {
            isBranch(word) -> {
                val imm24 = word and 0xFFFFFF
                val signed = (imm24 shl 8) shr 8
                pc += 8 + signed * 4
            }
            isBx(word) -> {
                val target = reg(word and 0xF)
                thumb = target and 1 == 1
                pc = target and 0xFFFFFFFE.toInt()
            }
            executeDataImmediate(word) -> Unit
            isStrhImmediate(word) -> {
                val rn = (word ushr 16) and 0xF
                val rd = (word ushr 12) and 0xF
                val offset = (((word ushr 8) and 0xF) shl 4) or (word and 0xF)
                writeHalf(reg(rn) + offset, reg(rd))
                pc += 4
            }
            executeLoadStore(word) -> Unit
            else -> pc += 4
        }
    }

    private fun executeThumb() {
        val instruction = readHalf(pc)
        if (instruction ushr 9 == 0x29) {
            val rm = (instruction ushr 6) and 7
            val rn = (instruction ushr 3) and 7
            val rd = instruction and 7
            writeHalf(registers[rn] + registers[rm], registers[rd])
            pc += 2
        } else {
            pc += 2
        }
    }

    private fun executeDataImmediate(word: Int): Boolean {
        if (word ushr 28 != 0xE) return false
        if ((word ushr 25) and 7 != 1) return false
        val opcode = (word ushr 21) and 0xF
        val value = armImmediate(word)
        val result = when (opcode) {
            0x4 -> reg((word ushr 16) and 0xF) + value
            0xD -> value
            else -> return false
        }
        writeReg((word ushr 12) and 0xF, result)
        return true
    }

    private fun executeLoadStore(word: Int): Boolean {
        if (word ushr 28 != 0xE) return false
        if ((word ushr 26) and 3 != 1) return false
        if ((word ushr 22) and 1 != 0) return false
        if ((word ushr 24) and 1 != 1 || (word ushr 23) and 1 != 1) return false
        if ((word ushr 21) and 1 != 0) return false
        val rn = (word ushr 16) and 0xF
        val rd = (word ushr 12) and 0xF
        val address = reg(rn) + (word and 0xFFF)
        if ((word ushr 20) and 1 == 1) {
            writeReg(rd, readWord(address))
        } else {
            writeWord(address, reg(rd))
            pc += 4
        }
        return true
    }

    private fun reg(index: Int): Int = if (index == 15) pc + if (thumb) 4 else 8 else registers[index]

    private fun writeReg(index: Int, value: Int) {
        if (index == 15) {
            pc = value
        } else {
            registers[index] = value
            pc += 4
        }
    }

    private fun readWord(address: Int): Int {
        val memory = memoryAt(address) ?: return 0
        val offset = address - memory.base
        if (offset < 0 || offset + 3 >= memory.bytes.size || offset % 4 != 0) return 0
        return (memory.bytes[offset].toInt() and 0xFF) or
            ((memory.bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((memory.bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((memory.bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readHalf(address: Int): Int {
        val memory = memoryAt(address) ?: return 0
        val offset = address - memory.base
        if (offset < 0 || offset + 1 >= memory.bytes.size || offset % 2 != 0) return 0
        return (memory.bytes[offset].toInt() and 0xFF) or
            ((memory.bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun writeWord(address: Int, value: Int) {
        val memory = memoryAt(address) ?: return
        if (!memory.writable) return
        val offset = address - memory.base
        if (offset < 0 || offset + 3 >= memory.bytes.size || offset % 4 != 0) return
        memory.bytes[offset] = value.toByte()
        memory.bytes[offset + 1] = (value ushr 8).toByte()
        memory.bytes[offset + 2] = (value ushr 16).toByte()
        memory.bytes[offset + 3] = (value ushr 24).toByte()
    }

    private fun writeHalf(address: Int, value: Int) {
        val memory = memoryAt(address) ?: return
        if (!memory.writable) return
        val offset = address - memory.base
        if (offset < 0 || offset + 1 >= memory.bytes.size || offset % 2 != 0) return
        memory.bytes[offset] = value.toByte()
        memory.bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun memoryAt(address: Int): Memory? = when {
        address in EWRAM_BASE until EWRAM_BASE + ewram.size -> Memory(EWRAM_BASE, ewram, writable = true)
        address in IWRAM_BASE until IWRAM_BASE + iwram.size -> Memory(IWRAM_BASE, iwram, writable = true)
        address in VRAM_BASE until VRAM_BASE + vram.size -> Memory(VRAM_BASE, vram, writable = true)
        address in ROM_BASE until ROM_BASE + rom.size -> Memory(ROM_BASE, rom, writable = false)
        else -> null
    }
}

private class Memory(
    val base: Int,
    val bytes: ByteArray,
    val writable: Boolean,
)

private fun isBranch(word: Int): Boolean = word ushr 24 == 0xEA

private fun isBx(word: Int): Boolean =
    word ushr 28 == 0xE && (word and 0x0FFFFFF0.toInt()) == 0x012FFF10

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
