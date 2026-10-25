package pelkidev.com.mx.emuladorgba.domain

private const val TITLE_OFFSET = 0xA0
private const val TITLE_LENGTH = 12

fun romTitle(rom: ByteArray): String? {
    if (rom.size < TITLE_OFFSET + TITLE_LENGTH) return null
    val raw = rom.copyOfRange(TITLE_OFFSET, TITLE_OFFSET + TITLE_LENGTH)
    val paddingAt = raw.indexOf(0)
    val end = if (paddingAt < 0) raw.size else paddingAt
    val head = raw.copyOfRange(0, end)
    val tail = raw.copyOfRange(end, raw.size)
    if (head.any { it < 0x20 || it > 0x7E }) return null
    if (tail.any { it != 0.toByte() && it != 0x20.toByte() }) return null
    val title = head.toString(Charsets.US_ASCII).trimEnd(' ')
    return title.ifEmpty { null }
}

fun romFrame(rom: ByteArray, fileName: String): GbaFrame =
    titleFrame(romTitle(rom) ?: fileName)
