package pelkidev.com.mx.emuladorgba.domain

private const val TITLE_OFFSET = 0xA0
private const val TITLE_LENGTH = 12

fun romTitle(rom: ByteArray): String? {
    if (rom.size < TITLE_OFFSET + TITLE_LENGTH) return null
    val raw = rom.copyOfRange(TITLE_OFFSET, TITLE_OFFSET + TITLE_LENGTH)
    if (raw.any { it < 0x20 || it > 0x7E }) return null
    val title = raw.toString(Charsets.US_ASCII).trimEnd(' ')
    return title.ifEmpty { null }
}
