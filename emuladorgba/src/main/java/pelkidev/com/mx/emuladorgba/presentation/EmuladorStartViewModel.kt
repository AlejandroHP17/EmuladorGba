package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.GbaMachine
import pelkidev.com.mx.emuladorgba.domain.isGbaFileName

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

    private var machine: GbaMachine? = null
    private var ticker: Job? = null
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onFileChosen(fileName: String, readBytes: () -> ByteArray?) {
        if (!isGbaFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val bytes = readBytes() ?: return
        loadedRom = bytes
        val next = GbaMachine(bytes)
        machine = next
        restartTicker()
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
                frame = next.frame(),
            )
        }
    }

    fun onMachineTick() {
        val running = machine ?: return
        running.step()
        val next = running.frame()
        _uiState.update { state ->
            val current = state.frame
            if (current != null && current.pixels.contentEquals(next.pixels)) {
                state
            } else {
                state.copy(frame = next)
            }
        }
    }

    private fun restartTicker() {
        ticker?.cancel()
        ticker = tickerScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                onMachineTick()
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 200L
    }
}
