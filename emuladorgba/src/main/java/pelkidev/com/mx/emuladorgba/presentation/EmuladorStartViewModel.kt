package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pelkidev.com.mx.emuladorgba.data.GbaCore
import pelkidev.com.mx.emuladorgba.data.MgbaCore
import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.isGbaFileName

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
    val frame: GbaFrame? = null,
    val samples: ByteArray = byteArrayOf(),
)

class EmuladorStartViewModel(
    private val startCore: (ByteArray) -> GbaCore = ::MgbaCore,
    private val autoAdvance: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    var loadedRom: ByteArray? = null
        private set

    private val coreLock = Any()

    @Volatile
    private var machine: GbaCore? = null
    private var heldButtons = emptySet<GbaButton>()
    private var ticker: Job? = null
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onFileChosen(fileName: String, readBytes: () -> ByteArray?) {
        if (!isGbaFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val bytes = readBytes() ?: return
        val next = try {
            startCore(bytes)
        } catch (_: IllegalStateException) {
            return
        }
        ticker?.cancel()
        synchronized(coreLock) {
            machine?.close()
            machine = next
        }
        loadedRom = bytes
        heldButtons = emptySet()
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
                frame = next.frame(),
                samples = byteArrayOf(),
            )
        }
        if (autoAdvance) startTicker()
    }

    fun onControlChanged(button: GbaButton, held: Boolean) {
        val running = machine ?: return
        val next = if (held) heldButtons + button else heldButtons - button
        if (next == heldButtons) return
        heldButtons = next
        running.hold(next)
    }

    fun onMachineTick() {
        val running: GbaCore
        val next: GbaFrame
        val heard: ByteArray
        synchronized(coreLock) {
            running = machine ?: return
            running.step()
            next = running.frame()
            heard = running.samples()
        }
        if (machine !== running) return
        _uiState.update { state ->
            val current = state.frame
            val sameFrame = current != null && current.pixels.contentEquals(next.pixels)
            val sameSound = state.samples.contentEquals(heard)
            when {
                sameFrame && sameSound && heard.isEmpty() -> state
                sameFrame -> state.copy(samples = heard)
                else -> state.copy(frame = next, samples = heard)
            }
        }
    }

    private fun startTicker() {
        ticker = tickerScope.launch {
            var deadline = System.nanoTime()
            while (isActive) {
                onMachineTick()
                deadline += FRAME_NANOS
                val wait = deadline - System.nanoTime()
                if (wait > 0) {
                    delay(wait / 1_000_000)
                } else {
                    deadline = System.nanoTime()
                }
            }
        }
    }

    override fun onCleared() {
        tickerScope.cancel()
        ticker = null
        synchronized(coreLock) {
            machine?.close()
            machine = null
        }
        _uiState.update { it.copy(samples = byteArrayOf()) }
        super.onCleared()
    }

    private companion object {
        const val FRAME_NANOS = 16_742_706L
    }
}
