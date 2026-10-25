package pelkidev.com.mx.emuladorgba.presentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.GbaMachine

class EmuladorStartViewModelTest {

    @Test
    fun otherExtension_doesNotLoadAndAsksForGba() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onFileChosen("notas.txt") { byteArrayOf(1) }

        assertNull(viewModel.uiState.value.loadedFileName)
        assertNull(viewModel.loadedRom)
        assertNull(viewModel.uiState.value.frame)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun otherExtension_keepsTheTitleAlreadyShown() {
        val viewModel = EmuladorStartViewModel()
        val rom = ByteArray(0xA0 + 12)
        "ZELDA       ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.uiState.value.frame

        viewModel.onFileChosen("notas.txt") { byteArrayOf(9) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.frame === frame)
        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_keepsTheBytesForThisSession() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(4, 5, 6)

        viewModel.onFileChosen("otro.GBA") { rom }

        assertEquals("otro.GBA", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertEquals(
            GbaFrame.WIDTH * GbaFrame.HEIGHT,
            viewModel.uiState.value.frame!!.pixels.size,
        )
        assertFalse(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_buildsTheFrameFromTheRomBytes() {
        val viewModel = EmuladorStartViewModel()
        val rom = ByteArray(0xA0 + 12)
        "TETRIS      ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)

        viewModel.onFileChosen("  otro.gba  ") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
    }

    @Test
    fun unreadGba_keepsThePreviousSession() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(7)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.uiState.value.frame

        viewModel.onFileChosen("otro.gba") { null }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.frame === frame)
    }

    @Test
    fun withoutAGba_thereIsNoFrame() {
        val viewModel = EmuladorStartViewModel()

        assertNull(viewModel.uiState.value.frame)
    }

    @Test
    fun anotherGba_replacesTheFrame() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        val first = viewModel.uiState.value.frame

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }

        assertNotNull(first)
        assertNotNull(viewModel.uiState.value.frame)
        assertTrue(viewModel.uiState.value.frame !== first)
    }

    @Test
    fun loadedGba_showsTheMachineFrame() {
        val viewModel = EmuladorStartViewModel()
        val rom = paintRedAtOrigin()

        viewModel.onFileChosen("juego.gba") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun whenTheMachinePaints_theShownFrameIsReplaced() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.onMachineTick()

        assertEquals(RED, viewModel.uiState.value.frame!!.pixel(0, 0))
        val painted = viewModel.uiState.value.frame
        viewModel.onMachineTick()
        assertTrue(viewModel.uiState.value.frame === painted)
    }

    @Test
    fun withoutARom_aTickDoesNotCreateAFrame() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onMachineTick()

        assertNull(viewModel.uiState.value.frame)
    }

    private fun paintRedAtOrigin(): ByteArray = words(
        0xE3A01406.toInt(),
        0xE3A0201F.toInt(),
        0xE1C120B0.toInt(),
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
