package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.isGbaFileName
import pelkidev.com.mx.emuladorgba.domain.stripeFrame

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
    val frame: GbaFrame? = null,
)

class EmuladorStartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    var loadedRom: ByteArray? = null
        private set

    fun onFileChosen(fileName: String, readBytes: () -> ByteArray?) {
        if (!isGbaFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val bytes = readBytes() ?: return
        loadedRom = bytes
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
                frame = stripeFrame(),
            )
        }
    }
}
