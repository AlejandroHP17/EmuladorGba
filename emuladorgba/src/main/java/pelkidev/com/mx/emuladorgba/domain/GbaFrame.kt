package pelkidev.com.mx.emuladorgba.domain

class GbaFrame(pixels: IntArray) {
    val pixels: IntArray = pixels.copyOf()

    init {
        require(pixels.size == WIDTH * HEIGHT) {
            "Un cuadro mide $WIDTH×$HEIGHT"
        }
    }

    fun pixel(x: Int, y: Int): Int = pixels[y * WIDTH + x]

    companion object {
        const val WIDTH = 240
        const val HEIGHT = 160
    }
}

private const val STRIPE_HEIGHT = 8
private const val STRIPE_DARK = 0xFF000000.toInt()
private const val STRIPE_LIGHT = 0xFFFFFFFF.toInt()

fun stripeFrame(): GbaFrame {
    val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT)
    for (y in 0 until GbaFrame.HEIGHT) {
        val color = if ((y / STRIPE_HEIGHT) % 2 == 0) STRIPE_DARK else STRIPE_LIGHT
        pixels.fill(color, y * GbaFrame.WIDTH, (y + 1) * GbaFrame.WIDTH)
    }
    return GbaFrame(pixels)
}

private const val GLYPH_WIDTH = 5
private const val GLYPH_HEIGHT = 7
private const val GLYPH_GAP = 1
private const val TITLE_SCALE = 3

fun titleFrame(title: String): GbaFrame {
    val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT) { STRIPE_DARK }
    val scale = titleScale(title)
    val textWidth = if (title.isEmpty()) {
        0
    } else {
        title.length * GLYPH_WIDTH * scale + (title.length - 1) * GLYPH_GAP * scale
    }
    var originX = (GbaFrame.WIDTH - textWidth) / 2
    val originY = (GbaFrame.HEIGHT - GLYPH_HEIGHT * scale) / 2
    for (character in title) {
        val rows = glyphRows(character)
        for (row in rows.indices) {
            for (col in 0 until GLYPH_WIDTH) {
                if (rows[row][col] != '1') continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val x = originX + col * scale + dx
                        val y = originY + row * scale + dy
                        if (x in 0 until GbaFrame.WIDTH && y in 0 until GbaFrame.HEIGHT) {
                            pixels[y * GbaFrame.WIDTH + x] = STRIPE_LIGHT
                        }
                    }
                }
            }
        }
        originX += (GLYPH_WIDTH + GLYPH_GAP) * scale
    }
    return GbaFrame(pixels)
}

private fun titleScale(title: String): Int {
    var scale = TITLE_SCALE
    while (scale > 1 && titleWidth(title, scale) > GbaFrame.WIDTH) {
        scale -= 1
    }
    return scale
}

private fun titleWidth(title: String, scale: Int): Int {
    if (title.isEmpty()) return 0
    return title.length * GLYPH_WIDTH * scale + (title.length - 1) * GLYPH_GAP * scale
}

private fun glyphRows(character: Char): List<String> {
    val letter = if (character in 'a'..'z') character.uppercaseChar() else character
    return GLYPHS[letter] ?: GLYPHS.getValue('?')
}

private val GLYPHS: Map<Char, List<String>> = mapOf(
    ' ' to glyph("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    '.' to glyph("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    '?' to glyph("01110", "10001", "00010", "00100", "00100", "00000", "00100"),
    'A' to glyph("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to glyph("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to glyph("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    'D' to glyph("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to glyph("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to glyph("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    'H' to glyph("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to glyph("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to glyph("00111", "00010", "00010", "00010", "10010", "10010", "01100"),
    'K' to glyph("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to glyph("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to glyph("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to glyph("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to glyph("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to glyph("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to glyph("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to glyph("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to glyph("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to glyph("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to glyph("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to glyph("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to glyph("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    'X' to glyph("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to glyph("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to glyph("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to glyph("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to glyph("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to glyph("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to glyph("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to glyph("10010", "10010", "10010", "11111", "00010", "00010", "00010"),
    '5' to glyph("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    '6' to glyph("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
    '7' to glyph("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to glyph("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to glyph("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
)

private fun glyph(vararg rows: String): List<String> = rows.toList()
