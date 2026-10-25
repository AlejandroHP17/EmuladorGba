package pelkidev.com.mx.emuladorgba.presentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

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
    fun otherExtension_keepsTheGameAlreadyLoaded() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(1, 2, 3)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.uiState.value.frame

        viewModel.onFileChosen("notas.txt") { byteArrayOf(9) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.frame === frame)
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
}
