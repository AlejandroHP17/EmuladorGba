package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import java.io.File
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
    val samples: ByteArray = byteArrayOf(),
)

class EmuladorStartViewModel(
    private val startCore: (ByteArray) -> GbaCore = ::MgbaCore,
    private val autoAdvance: Boolean = true,
    private val stateDirectory: File? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    private val _frame = MutableStateFlow<GbaFrame?>(null)
    val frame: StateFlow<GbaFrame?> = _frame.asStateFlow()

    var loadedRom: ByteArray? = null
        private set

    private val coreLock = Any()

    @Volatile
    private var machine: GbaCore? = null
    private var heldButtons = emptySet<GbaButton>()
    private var ticker: Job? = null
    private var paused = false
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
            next.hold(emptySet())
            machine = next
        }
        loadedRom = bytes
        heldButtons = emptySet()
        paused = false
        _frame.value = next.frame()
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
                samples = byteArrayOf(),
            )
        }
        if (autoAdvance) startTicker()
    }

    fun onSaveState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val bytes = synchronized(coreLock) { machine?.exportState() } ?: return
        if (bytes.isEmpty()) return
        directory.mkdirs()
        stateFile(directory, name).writeBytes(bytes)
    }

    fun onLoadState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val file = stateFile(directory, name)
        if (!file.isFile) return
        val restored: GbaFrame
        synchronized(coreLock) {
            val running = machine ?: return
            if (!running.importState(file.readBytes())) return
            restored = running.frame()
        }
        _frame.value = restored
    }

    fun onControlChanged(button: GbaButton, held: Boolean) {
        val running = machine ?: return
        val next = if (held) heldButtons + button else heldButtons - button
        if (next == heldButtons) return
        heldButtons = next
        running.hold(next)
    }

    fun onAppStopped() {
        if (paused) return
        paused = true
        ticker?.cancel()
        ticker = null
        heldButtons = emptySet()
        synchronized(coreLock) {
            machine?.hold(emptySet())
            machine?.pause()
        }
    }

    fun onAppStarted() {
        if (!paused) return
        paused = false
        synchronized(coreLock) {
            machine?.resume()
        }
        if (autoAdvance && machine != null) startTicker()
    }

    fun onMachineTick() {
        if (paused) return
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
        val current = _frame.value
        if (current == null || !current.pixels.contentEquals(next.pixels)) _frame.value = next
        _uiState.update { state ->
            if (heard.isEmpty() && state.samples.isEmpty()) state else state.copy(samples = heard)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
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

    fun onLeave() {
        ticker?.cancel()
        ticker = null
        paused = true
        synchronized(coreLock) {
            machine?.close()
            machine = null
        }
        _uiState.update { it.copy(samples = byteArrayOf()) }
    }

    override fun onCleared() {
        tickerScope.cancel()
        onLeave()
        super.onCleared()
    }

    private companion object {
        const val FRAME_NANOS = 16_742_706L

        fun stateFile(directory: File, name: String): File {
            val safe = name.replace("/", "_").replace("\\", "_")
            return File(directory, safe)
        }
    }
}
