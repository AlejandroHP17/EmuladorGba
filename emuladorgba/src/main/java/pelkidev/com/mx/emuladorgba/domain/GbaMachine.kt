package pelkidev.com.mx.emuladorgba.domain

private const val IO_BASE = 0x04000000
private const val EWRAM_BASE = 0x02000000
private const val IWRAM_BASE = 0x03000000
private const val PALETTE_BASE = 0x05000000
private const val VRAM_BASE = 0x06000000
private const val ROM_BASE = 0x08000000
private const val INSTRUCTIONS_PER_STEP = 50_000
private const val BLACK = 0xFF000000.toInt()

class GbaMachine(private val rom: ByteArray) {
    private val registers = IntArray(16)
    private val ewram = ByteArray(256 * 1024)
    private val iwram = ByteArray(32 * 1024)
    private val io = ByteArray(0x400)
    private val palette = ByteArray(0x200)
    private val vram = ByteArray(96 * 1024)
    private var pc = ROM_BASE
    private var thumb = false
    private var scanline = 0
    private val save = ByteArray(64 * 1024) { 0xFF.toByte() }
    private var saveMode = 0
    private var saveStep = 0
    private var transferring = false
    private var servingBlank = false
    private var flagN = false
    private var flagZ = false
    private var flagC = false
    private var flagV = false

    init {
        io[0x130] = 0xFF.toByte()
        io[0x131] = 0x03
    }

    fun step() {
        repeat(INSTRUCTIONS_PER_STEP) {
            executeOne()
        }
    }

    fun frame(): GbaFrame {
        val display = readHalf(IO_BASE)
        val enabled = (display ushr 8) and 0xF
        if ((display and 7) != 0 || enabled == 0) return photoFrame()
        val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { rgb555(readHalf(PALETTE_BASE)) }
        (0..3).filter { (enabled ushr it) and 1 == 1 }
            .sortedWith(compareByDescending<Int> { readHalf(0x04000008 + it * 2) and 3 }.thenByDescending { it })
            .forEach { paintBackground(it, pixels) }
        return GbaFrame(pixels)
    }

    private fun photoFrame(): GbaFrame {
        val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { index ->
            val lo = vram[index * 2].toInt() and 0xFF
            val hi = vram[index * 2 + 1].toInt() and 0xFF
            rgb555(lo or (hi shl 8))
        }
        return GbaFrame(pixels)
    }

    private fun paintBackground(background: Int, pixels: IntArray) {
        val control = readHalf(0x04000008 + background * 2)
        val eightBit = control and 0x80 != 0
        val charBase = ((control ushr 2) and 3) * 0x4000
        val screenBase = ((control ushr 8) and 0x1F) * 0x800
        val wide = (control ushr 14) and 1 == 1
        val tall = (control ushr 15) and 1 == 1
        val tilesWide = if (wide) 64 else 32
        val tilesTall = if (tall) 64 else 32
        val scrollX = readHalf(0x04000010 + background * 4) and 0x1FF
        val scrollY = readHalf(0x04000012 + background * 4) and 0x1FF
        for (y in 0 until GbaFrame.HEIGHT) {
            val mapY = (y + scrollY) and (tilesTall * 8 - 1)
            for (x in 0 until GbaFrame.WIDTH) {
                val mapX = (x + scrollX) and (tilesWide * 8 - 1)
                val color = backgroundColor(eightBit, charBase, screenBase, wide, mapX, mapY) ?: continue
                pixels[y * GbaFrame.WIDTH + x] = color
            }
        }
    }

    private fun backgroundColor(
        eightBit: Boolean,
        charBase: Int,
        screenBase: Int,
        wide: Boolean,
        mapX: Int,
        mapY: Int,
    ): Int? {
        val tileColumn = mapX ushr 3
        val tileRow = mapY ushr 3
        val block = (tileRow ushr 5) * (if (wide) 2 else 1) + (tileColumn ushr 5)
        val entryAddress = VRAM_BASE + screenBase + block * 0x800 +
            ((tileRow and 31) * 32 + (tileColumn and 31)) * 2
        val entry = readHalf(entryAddress)
        var pixelX = mapX and 7
        var pixelY = mapY and 7
        if (entry and 0x400 != 0) pixelX = 7 - pixelX
        if (entry and 0x800 != 0) pixelY = 7 - pixelY
        val tile = entry and 0x3FF
        val index = if (eightBit) {
            readByte(VRAM_BASE + charBase + tile * 64 + pixelY * 8 + pixelX)
        } else {
            val packed = readByte(VRAM_BASE + charBase + tile * 32 + pixelY * 4 + (pixelX ushr 1))
            if (pixelX and 1 == 0) packed and 0xF else (packed ushr 4) and 0xF
        }
        if (index == 0) return null
        val colorAddress = if (eightBit) {
            PALETTE_BASE + index * 2
        } else {
            PALETTE_BASE + (((entry ushr 12) and 0xF) * 16 + index) * 2
        }
        return rgb555(readHalf(colorAddress))
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
            isBx(word) -> jump(reg(word and 0xF))
            isSoftwareInterrupt(word) -> {
                softwareInterrupt(word and 0xFFFFFF)
                pc += 4
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
        val rd = instruction and 7
        val rn = (instruction ushr 3) and 7
        when {
            instruction ushr 11 == 0b00011 -> {
                val immediate = (instruction ushr 10) and 1 == 1
                val subtract = (instruction ushr 9) and 1 == 1
                val left = registers[(instruction ushr 3) and 7]
                val right = if (immediate) (instruction ushr 6) and 7 else registers[(instruction ushr 6) and 7]
                registers[rd] = if (subtract) subFlags(left, right) else addFlags(left, right)
                pc += 2
            }
            instruction ushr 13 == 0b000 -> {
                val amount = (instruction ushr 6) and 0x1F
                val source = registers[(instruction ushr 3) and 7]
                registers[rd] = when ((instruction ushr 11) and 3) {
                    0 -> lsl(source, amount)
                    1 -> lsr(source, if (amount == 0) 32 else amount)
                    else -> asr(source, if (amount == 0) 32 else amount)
                }
                pc += 2
            }
            instruction ushr 13 == 0b001 -> {
                val value = instruction and 0xFF
                val current = registers[(instruction ushr 8) and 7]
                when ((instruction ushr 11) and 3) {
                    0 -> {
                        registers[(instruction ushr 8) and 7] = value
                        rememberNz(value)
                    }
                    1 -> subFlags(current, value)
                    2 -> registers[(instruction ushr 8) and 7] = addFlags(current, value)
                    else -> registers[(instruction ushr 8) and 7] = subFlags(current, value)
                }
                pc += 2
            }
            instruction ushr 10 == 0b010000 -> executeThumbAlu(instruction)
            instruction ushr 10 == 0b010001 -> executeThumbHigh(instruction)
            instruction ushr 11 == 0b01001 -> {
                val address = (pc + 4) and 0xFFFFFFFC.toInt()
                registers[(instruction ushr 8) and 7] = readWord(address + (instruction and 0xFF) * 4)
                pc += 2
            }
            instruction ushr 12 == 0b0101 -> executeThumbRegisterTransfer(instruction)
            instruction ushr 13 == 0b011 -> {
                val byte = (instruction ushr 12) and 1 == 1
                val load = (instruction ushr 11) and 1 == 1
                val offset = (instruction ushr 6) and 0x1F
                val address = registers[rn] + if (byte) offset else offset * 4
                transfer(address, rd, load, byte, half = false)
                pc += 2
            }
            instruction ushr 12 == 0b1000 -> {
                val load = (instruction ushr 11) and 1 == 1
                val address = registers[rn] + ((instruction ushr 6) and 0x1F) * 2
                transfer(address, rd, load, byte = false, half = true)
                pc += 2
            }
            instruction ushr 12 == 0b1001 -> {
                val load = (instruction ushr 11) and 1 == 1
                val address = registers[13] + (instruction and 0xFF) * 4
                transfer(address, (instruction ushr 8) and 7, load, byte = false, half = false)
                pc += 2
            }
            instruction ushr 12 == 0b1010 -> {
                val base = if ((instruction ushr 11) and 1 == 1) registers[13] else (pc + 4) and 0xFFFFFFFC.toInt()
                registers[(instruction ushr 8) and 7] = base + (instruction and 0xFF) * 4
                pc += 2
            }
            instruction ushr 12 == 0b1011 && (instruction ushr 9) and 0b11 == 0b10 -> {
                executeThumbStack(instruction)
            }
            (instruction ushr 8) == 0b10110000 -> {
                val amount = (instruction and 0x7F) * 4
                registers[13] += if ((instruction ushr 7) and 1 == 1) -amount else amount
                pc += 2
            }
            instruction ushr 12 == 0b1100 -> {
                executeThumbBlock(instruction)
                pc += 2
            }
            instruction ushr 12 == 0b1101 -> executeThumbBranch(instruction)
            instruction ushr 11 == 0b11100 -> {
                pc += 4 + (signExtend(instruction and 0x7FF, 11) shl 1)
            }
            instruction ushr 11 == 0b11110 -> {
                registers[14] = pc + 4 + (signExtend(instruction and 0x7FF, 11) shl 12)
                pc += 2
            }
            instruction ushr 11 == 0b11111 -> {
                val next = (pc + 2) or 1
                pc = registers[14] + ((instruction and 0x7FF) shl 1)
                registers[14] = next
            }
            else -> pc += 2
        }
    }

    private fun executeThumbAlu(instruction: Int) {
        val op = (instruction ushr 6) and 0xF
        val source = registers[(instruction ushr 3) and 7]
        val other = registers[instruction and 7]
        val amount = source and 0xFF
        val result = when (op) {
            0 -> source and other
            1 -> source xor other
            2 -> lsl(other, amount)
            3 -> lsr(other, amount)
            4 -> asr(other, amount)
            5 -> addFlags(other, source, if (flagC) 1 else 0)
            6 -> addFlags(other, source.inv(), if (flagC) 1 else 0)
            7 -> ror(other, amount)
            8 -> source and other
            9 -> subFlags(0, source)
            10 -> subFlags(other, source)
            11 -> addFlags(other, source)
            12 -> source or other
            13 -> other * source
            14 -> other and source.inv()
            else -> source.inv()
        }
        if (op != 8 && op != 10 && op != 11) {
            registers[instruction and 7] = result
            if (op !in intArrayOf(2, 3, 4, 5, 6, 7, 9)) rememberNz(result)
        }
        pc += 2
    }

    private fun executeThumbHigh(instruction: Int) {
        val op = (instruction ushr 8) and 3
        val rs = ((instruction ushr 3) and 7) + if ((instruction ushr 6) and 1 == 1) 8 else 0
        val rd = (instruction and 7) + if ((instruction ushr 7) and 1 == 1) 8 else 0
        when (op) {
            0 -> setCore(rd, reg(rd) + reg(rs))
            1 -> subFlags(reg(rd), reg(rs))
            2 -> setCore(rd, reg(rs))
            else -> {
                jump(reg(rs))
                return
            }
        }
        if (rd != 15) pc += 2
    }

    private fun executeThumbRegisterTransfer(instruction: Int) {
        val rm = (instruction ushr 6) and 7
        val rn = (instruction ushr 3) and 7
        val rd = instruction and 7
        val address = registers[rn] + registers[rm]
        when ((instruction ushr 9) and 7) {
            0 -> writeWord(address, registers[rd])
            1 -> writeHalf(address, registers[rd])
            2 -> writeByte(address, registers[rd])
            3 -> registers[rd] = signByte(readByte(address))
            4 -> registers[rd] = readWord(address)
            5 -> registers[rd] = readHalf(address)
            6 -> registers[rd] = readByte(address)
            else -> registers[rd] = signHalf(readHalf(address))
        }
        pc += 2
    }

    private fun executeThumbStack(instruction: Int) {
        val load = (instruction ushr 11) and 1 == 1
        val extra = (instruction ushr 8) and 1 == 1
        val list = instruction and 0xFF
        var count = Integer.bitCount(list) + if (extra) 1 else 0
        var address = if (load) registers[13] else registers[13] - 4 * count
        for (index in 0 until 8) {
            if ((list ushr index) and 1 == 0) continue
            if (load) registers[index] = readWord(address) else writeWord(address, registers[index])
            address += 4
        }
        if (extra) {
            if (load) {
                jump(readWord(address))
            } else {
                writeWord(address, registers[14])
            }
            address += 4
        }
        registers[13] = if (load) address else registers[13] - 4 * count
        if (!load || !extra) pc += 2
    }

    private fun executeThumbBlock(instruction: Int) {
        val load = (instruction ushr 11) and 1 == 1
        val base = (instruction ushr 8) and 7
        val list = instruction and 0xFF
        var address = registers[base]
        for (index in 0 until 8) {
            if ((list ushr index) and 1 == 0) continue
            if (load) registers[index] = readWord(address) else writeWord(address, registers[index])
            address += 4
        }
        registers[base] = address
    }

    private fun executeThumbBranch(instruction: Int) {
        val condition = (instruction ushr 8) and 0xF
        if (condition == 15) {
            softwareInterrupt(instruction and 0xFF)
            pc += 2
            return
        }
        if (condition >= 14 || !conditionHolds(condition)) {
            pc += 2
            return
        }
        pc += 4 + (signExtend(instruction and 0xFF, 8) shl 1)
    }

    private fun transfer(address: Int, rd: Int, load: Boolean, byte: Boolean, half: Boolean) {
        if (load) {
            registers[rd] = when {
                byte -> readByte(address)
                half -> readHalf(address)
                else -> readWord(address)
            }
        } else {
            when {
                byte -> writeByte(address, registers[rd])
                half -> writeHalf(address, registers[rd])
                else -> writeWord(address, registers[rd])
            }
        }
    }

    private fun jump(target: Int) {
        thumb = target and 1 == 1
        pc = target and if (thumb) 0xFFFFFFFE.toInt() else 0xFFFFFFFC.toInt()
    }

    private fun setCore(index: Int, value: Int) {
        if (index == 15) {
            pc = value and if (thumb) 0xFFFFFFFE.toInt() else 0xFFFFFFFC.toInt()
        } else {
            registers[index] = value
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
        if ((word ushr 25) and 1 != 0) return false
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
        if (address % 4 != 0) return 0
        return readHalf(address) or (readHalf(address + 2) shl 16)
    }

    private fun readHalf(address: Int): Int {
        if (address % 2 != 0) return 0
        return readByte(address) or (readByte(address + 1) shl 8)
    }

    private fun readByte(address: Int): Int {
        if (address == 0x04000004) return if (scanline % 228 >= 160) 1 else 0
        if (address == 0x04000006) {
            val line = scanline % 228
            scanline += 1
            return line
        }
        if (address in 0x0E000000 until 0x0E010000) return readSave(address)
        val memory = memoryAt(address) ?: return 0
        val offset = address - memory.base
        if (offset !in memory.bytes.indices) return 0
        return memory.bytes[offset].toInt() and 0xFF
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
        directCopy(address)
        directCopy(address + 2)
    }

    private fun readSave(address: Int): Int {
        val offset = address - 0x0E000000
        if (saveMode == 1) {
            return when (offset) {
                0 -> 0xBF
                1 -> 0xD4
                else -> 0xFF
            }
        }
        return save[offset].toInt() and 0xFF
    }

    private fun writeSave(address: Int, value: Int) {
        val offset = address - 0x0E000000
        if (offset !in save.indices) return
        val byte = value and 0xFF
        val port = offset and 0xFFFF
        if (byte == 0xF0 && saveStep != 4) {
            saveMode = 0
            saveStep = 0
            return
        }
        when (saveStep) {
            0 -> if (port == 0x5555 && byte == 0xAA) saveStep = 1
            1 -> saveStep = if (port == 0x2AAA && byte == 0x55) 2 else 0
            2 -> {
                saveStep = 0
                when {
                    byte == 0x90 && port == 0x5555 -> saveMode = 1
                    byte == 0xF0 -> saveMode = 0
                    byte == 0xA0 && port == 0x5555 -> saveStep = 4
                    byte == 0x80 && port == 0x5555 -> saveStep = 5
                }
            }
            4 -> {
                save[offset] = (save[offset].toInt() and byte).toByte()
                saveMode = 0
                saveStep = 0
            }
            5 -> saveStep = if (port == 0x5555 && byte == 0xAA) 6 else 0
            6 -> saveStep = if (port == 0x2AAA && byte == 0x55) 7 else 0
            else -> {
                saveStep = 0
                saveMode = 0
                when {
                    byte == 0x10 && port == 0x5555 -> save.fill(0xFF.toByte())
                    byte == 0x30 -> {
                        val start = offset and 0xFFF.inv()
                        for (index in start until (start + 4096).coerceAtMost(save.size)) {
                            save[index] = 0xFF.toByte()
                        }
                    }
                }
            }
        }
    }

    private fun writeByte(address: Int, value: Int) {
        if (address in 0x0E000000 until 0x0E010000) {
            writeSave(address, value)
            return
        }
        val memory = memoryAt(address) ?: return
        if (!memory.writable) return
        val offset = address - memory.base
        if (offset !in memory.bytes.indices) return
        memory.bytes[offset] = value.toByte()
    }

    private fun writeHalf(address: Int, value: Int) {
        val memory = memoryAt(address) ?: return
        if (!memory.writable) return
        val offset = address - memory.base
        if (offset < 0 || offset + 1 >= memory.bytes.size || offset % 2 != 0) return
        memory.bytes[offset] = value.toByte()
        memory.bytes[offset + 1] = (value ushr 8).toByte()
        directCopy(address)
    }

    private fun directCopy(address: Int) {
        if (transferring) return
        val channel = when (address) {
            0x040000B8, 0x040000BA -> 0
            0x040000C4, 0x040000C6 -> 1
            0x040000D0, 0x040000D2 -> 2
            0x040000DC, 0x040000DE -> 3
            else -> return
        }
        val base = 0x040000B0 + channel * 12
        val control = readHalf(base + 10)
        if (control and 0x8000 == 0 || (control ushr 12) and 3 != 0) return
        performCopy(channel, control, keepEnabled = false)
    }

    private fun performCopy(channel: Int, control: Int, keepEnabled: Boolean) {
        if (transferring) return
        transferring = true
        val base = 0x040000B0 + channel * 12
        val wide = control and 0x0400 != 0
        val step = if (wide) 4 else 2
        var count = readHalf(base + 8)
        if (count == 0) count = if (channel == 3) 0x10000 else 0x4000
        var source = readWord(base) and (step - 1).inv()
        var dest = readWord(base + 4) and (step - 1).inv()
        val sourceMode = (control ushr 7) and 3
        val destMode = (control ushr 5) and 3
        repeat(count) {
            if (wide) writeWord(dest, readWord(source)) else writeHalf(dest, readHalf(source))
            source = movePointer(source, sourceMode, step)
            dest = movePointer(dest, destMode, step)
        }
        if (!keepEnabled) {
            val cleared = control and 0x7FFF
            val offset = base + 10 - IO_BASE
            io[offset] = cleared.toByte()
            io[offset + 1] = (cleared ushr 8).toByte()
        }
        transferring = false
    }

    private fun movePointer(address: Int, mode: Int, step: Int): Int = when (mode) {
        1 -> address - step
        2 -> address
        else -> address + step
    }

    private fun memoryAt(address: Int): Memory? = when {
        address in IO_BASE until IO_BASE + io.size -> Memory(IO_BASE, io, writable = true)
        address in EWRAM_BASE until EWRAM_BASE + ewram.size -> Memory(EWRAM_BASE, ewram, writable = true)
        address in IWRAM_BASE until IWRAM_BASE + iwram.size -> Memory(IWRAM_BASE, iwram, writable = true)
        address in PALETTE_BASE until PALETTE_BASE + palette.size -> Memory(PALETTE_BASE, palette, writable = true)
        address in VRAM_BASE until VRAM_BASE + vram.size -> Memory(VRAM_BASE, vram, writable = true)
        address in ROM_BASE until ROM_BASE + rom.size -> Memory(ROM_BASE, rom, writable = false)
        else -> null
    }

    private fun softwareInterrupt(number: Int) {
        when (number) {
            0x05 -> serviceVerticalBlank()
            0x0B -> cpuTransfer(words = (registers[2] ushr 26) and 1 == 1, fill = (registers[2] ushr 24) and 1 == 1, count = registers[2] and 0x1FFFFF)
            0x0C -> cpuTransfer(words = true, fill = (registers[2] ushr 24) and 1 == 1, count = (registers[2] and 0x1FFFFF) + 7 and 7.inv())
            0x11, 0x12 -> lz77(registers[0], registers[1])
        }
    }

    private fun serviceVerticalBlank() {
        copyOnVerticalBlank()
        if (servingBlank) return
        val handler = readWord(0x03004F80)
        if (handler == 0) return
        servingBlank = true
        val savedPc = pc
        val savedThumb = thumb
        val saved = registers.copyOf()
        val savedN = flagN
        val savedZ = flagZ
        val savedC = flagC
        val savedV = flagV
        registers[14] = 0x01FFFFFC
        jump(handler)
        var guard = 0
        while (pc != 0x01FFFFFC && guard < 8_000) {
            executeOne()
            guard += 1
        }
        pc = savedPc
        thumb = savedThumb
        saved.copyInto(registers)
        flagN = savedN
        flagZ = savedZ
        flagC = savedC
        flagV = savedV
        servingBlank = false
    }

    private fun copyOnVerticalBlank() {
        for (channel in 0..3) {
            val control = readHalf(0x040000B0 + channel * 12 + 10)
            if (control and 0x8000 != 0 && (control ushr 12) and 3 == 1) {
                performCopy(channel, control, keepEnabled = control and 0x0200 != 0)
            }
        }
    }

    private fun cpuTransfer(words: Boolean, fill: Boolean, count: Int) {
        val step = if (words) 4 else 2
        var source = registers[0] and (step - 1).inv()
        var dest = registers[1] and (step - 1).inv()
        val value = if (words) readWord(source) else readHalf(source)
        repeat(count) {
            if (words) writeWord(dest, if (fill) value else readWord(source)) else writeHalf(dest, if (fill) value else readHalf(source))
            dest += step
            if (!fill) source += step
        }
    }

    private fun lz77(sourceAddress: Int, destAddress: Int) {
        val header = readWord(sourceAddress)
        if ((header ushr 4) and 0xF != 1) return
        var source = sourceAddress + 4
        var dest = destAddress
        var remaining = header ushr 8
        while (remaining > 0) {
            val flags = readByte(source)
            source += 1
            var bit = 0x80
            while (bit != 0 && remaining > 0) {
                if (flags and bit == 0) {
                    writeByte(dest, readByte(source))
                    source += 1
                    dest += 1
                    remaining -= 1
                } else {
                    val block = (readByte(source) shl 8) or readByte(source + 1)
                    source += 2
                    val length = (block ushr 12) + 3
                    val displacement = (block and 0xFFF) + 1
                    repeat(length) {
                        if (remaining <= 0) return@repeat
                        writeByte(dest, readByte(dest - displacement))
                        dest += 1
                        remaining -= 1
                    }
                }
                bit = bit ushr 1
            }
        }
    }

    private fun rememberNz(value: Int) {
        flagN = value < 0
        flagZ = value == 0
    }

    private fun addFlags(left: Int, right: Int, carry: Int = 0): Int {
        val sum = left.toLong() + (right.toLong() and 0xFFFFFFFFL) + carry
        val result = sum.toInt()
        rememberNz(result)
        flagC = sum ushr 32 != 0L
        flagV = ((left xor result) and (right xor result)) < 0
        return result
    }

    private fun subFlags(left: Int, right: Int): Int = addFlags(left, right.inv(), 1)

    private fun lsl(value: Int, amount: Int): Int {
        if (amount <= 0) return value
        if (amount in 1..31) {
            flagC = (value ushr (32 - amount)) and 1 == 1
            val result = value shl amount
            rememberNz(result)
            return result
        }
        flagC = amount == 32 && value and 1 == 1
        rememberNz(0)
        return 0
    }

    private fun lsr(value: Int, amount: Int): Int {
        if (amount <= 0) return value
        if (amount in 1..32) {
            flagC = (value ushr (amount - 1)) and 1 == 1
            val result = if (amount == 32) 0 else value ushr amount
            rememberNz(result)
            return result
        }
        flagC = false
        rememberNz(0)
        return 0
    }

    private fun asr(value: Int, amount: Int): Int {
        if (amount <= 0) return value
        if (amount in 1..31) {
            flagC = (value ushr (amount - 1)) and 1 == 1
            val result = value shr amount
            rememberNz(result)
            return result
        }
        flagC = value < 0
        val result = if (value < 0) -1 else 0
        rememberNz(result)
        return result
    }

    private fun ror(value: Int, amount: Int): Int {
        val shift = amount and 31
        if (amount == 0) return value
        if (shift == 0) {
            flagC = value < 0
            rememberNz(value)
            return value
        }
        val result = (value ushr shift) or (value shl (32 - shift))
        flagC = (result ushr 31) and 1 == 1
        rememberNz(result)
        return result
    }

    private fun conditionHolds(condition: Int): Boolean = when (condition) {
        0 -> flagZ
        1 -> !flagZ
        2 -> flagC
        3 -> !flagC
        4 -> flagN
        5 -> !flagN
        6 -> flagV
        7 -> !flagV
        8 -> flagC && !flagZ
        9 -> !flagC || flagZ
        10 -> flagN == flagV
        11 -> flagN != flagV
        12 -> !flagZ && flagN == flagV
        13 -> flagZ || flagN != flagV
        else -> true
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

private fun isSoftwareInterrupt(word: Int): Boolean = word ushr 24 == 0xEF

private fun isStrhImmediate(word: Int): Boolean =
    (word and 0x0FF000F0.toInt()) == 0x01C000B0

private fun armImmediate(word: Int): Int {
    val rotate = ((word ushr 8) and 0xF) * 2
    return Integer.rotateRight(word and 0xFF, rotate)
}

private fun signExtend(value: Int, bits: Int): Int {
    val shift = 32 - bits
    return (value shl shift) shr shift
}

private fun signByte(value: Int): Int = (value shl 24) shr 24

private fun signHalf(value: Int): Int = (value shl 16) shr 16

private fun rgb555(color: Int): Int {
    if (color == 0) return BLACK
    val red = (color and 0x1F) * 255 / 31
    val green = ((color ushr 5) and 0x1F) * 255 / 31
    val blue = ((color ushr 10) and 0x1F) * 255 / 31
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
