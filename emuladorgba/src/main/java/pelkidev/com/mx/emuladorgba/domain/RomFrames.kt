package pelkidev.com.mx.emuladorgba.domain

private const val LOGO_OFFSET = 0x04
private const val LOGO_LENGTH = 156
private const val LOGO_BYTES_PER_ROW = 13
private const val LOGO_SCALE = 2
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

class RomFrames(
    val title: GbaFrame,
    val graphic: GbaFrame,
) {
    fun frameAt(step: Int): GbaFrame = if (step % 2 == 0) title else graphic
}

fun romFrames(rom: ByteArray, fileName: String): RomFrames =
    RomFrames(
        title = titleFrame(romTitle(rom) ?: fileName),
        graphic = headerGraphicFrame(rom),
    )

fun headerGraphicFrame(rom: ByteArray): GbaFrame {
    val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { BLACK }
    val available = (rom.size - LOGO_OFFSET).coerceAtLeast(0).coerceAtMost(LOGO_LENGTH)
    val pixelWidth = LOGO_BYTES_PER_ROW * 8 * LOGO_SCALE
    val pixelHeight = (LOGO_LENGTH / LOGO_BYTES_PER_ROW) * LOGO_SCALE
    val originX = (GbaFrame.WIDTH - pixelWidth) / 2
    val originY = (GbaFrame.HEIGHT - pixelHeight) / 2
    for (index in 0 until available) {
        val bits = rom[LOGO_OFFSET + index].toInt() and 0xFF
        val row = index / LOGO_BYTES_PER_ROW
        val column = (index % LOGO_BYTES_PER_ROW) * 8
        for (bit in 0 until 8) {
            if ((bits ushr (7 - bit)) and 1 == 0) continue
            val graphicX = column + bit
            val graphicY = row
            for (dy in 0 until LOGO_SCALE) {
                for (dx in 0 until LOGO_SCALE) {
                    val x = originX + graphicX * LOGO_SCALE + dx
                    val y = originY + graphicY * LOGO_SCALE + dy
                    pixels[y * GbaFrame.WIDTH + x] = WHITE
                }
            }
        }
    }
    return GbaFrame(pixels)
}
