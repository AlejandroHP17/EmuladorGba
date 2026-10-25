package pelkidev.com.mx.emuladorgba.presentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmuladorStartViewModelTest {

    @Test
    fun otherExtension_doesNotLoadAndAsksForGba() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onFileChosen("notas.txt") { byteArrayOf(1) }

        assertNull(viewModel.uiState.value.loadedFileName)
        assertNull(viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun otherExtension_keepsTheGameAlreadyLoaded() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(1, 2, 3)
        viewModel.onFileChosen("juego.gba") { rom }

        viewModel.onFileChosen("notas.txt") { byteArrayOf(9) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_keepsTheBytesForThisSession() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(4, 5, 6)

        viewModel.onFileChosen("otro.GBA") { rom }

        assertEquals("otro.GBA", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertFalse(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun unreadGba_keepsThePreviousSession() {
        val viewModel = EmuladorStartViewModel()
        val rom = byteArrayOf(7)
        viewModel.onFileChosen("juego.gba") { rom }

        viewModel.onFileChosen("otro.gba") { null }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
    }
}
