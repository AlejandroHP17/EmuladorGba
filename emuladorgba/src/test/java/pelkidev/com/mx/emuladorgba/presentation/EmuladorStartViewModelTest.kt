package pelkidev.com.mx.emuladorgba.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmuladorStartViewModelTest {

    @Test
    fun otherExtension_doesNotLoadAndAsksForGba() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onFileNameChosen("notas.txt")

        assertNull(viewModel.uiState.value.loadedFileName)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun otherExtension_keepsTheGameAlreadyLoaded() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileNameChosen("juego.gba")

        viewModel.onFileNameChosen("notas.txt")

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_replacesTheLoadedGameAndClearsTheWarning() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileNameChosen("notas.txt")

        viewModel.onFileNameChosen("otro.GBA")

        assertEquals("otro.GBA", viewModel.uiState.value.loadedFileName)
        assertFalse(viewModel.uiState.value.showInvalidFileMessage)
    }
}
