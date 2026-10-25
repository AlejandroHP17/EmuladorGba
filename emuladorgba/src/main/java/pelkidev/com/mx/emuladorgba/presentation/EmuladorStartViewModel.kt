package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pelkidev.com.mx.emuladorgba.domain.isGbaFileName

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
)

class EmuladorStartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    fun onFileNameChosen(fileName: String) {
        if (!isGbaFileName(fileName)) return
        _uiState.update { it.copy(loadedFileName = fileName.trim()) }
    }
}
