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

enum class ReplaceAsk { Save, Leave }

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
    val samples: ByteArray = byteArrayOf(),
    val selectedSlot: Int = 1,
    val slotNames: List<String> = listOf("", "", ""),
    val replaceAsk: ReplaceAsk? = null,
    val replaceLabel: String = "",
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
        val trimmed = fileName.trim()
        ticker?.cancel()
        saveCartridge()
        val cartridge = cartridgeBytes(trimmed)
        synchronized(coreLock) {
            machine?.close()
            next.hold(emptySet())
            if (cartridge != null) next.importCartridge(cartridge)
            machine = next
        }
        loadedRom = bytes
        heldButtons = emptySet()
        paused = false
        _frame.value = next.frame()
        _uiState.update {
            it.copy(
                loadedFileName = trimmed,
                showInvalidFileMessage = false,
                samples = byteArrayOf(),
                selectedSlot = 1,
                slotNames = readSlotNames(trimmed),
            )
        }
        if (autoAdvance) startTicker()
    }

    fun onSlotSelected(slot: Int) {
        if (slot !in 1..3) return
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    fun onSlotNamed(name: String) {
        val fileName = _uiState.value.loadedFileName ?: return
        val slot = _uiState.value.selectedSlot
        val limited = name.take(SLOT_NAME_LIMIT)
        _uiState.update { state ->
            val names = state.slotNames.toMutableList()
            while (names.size < 3) names.add("")
            names[slot - 1] = limited
            state.copy(slotNames = names)
        }
        writeSlotName(fileName, slot, limited)
    }

    private fun readSlotNames(fileName: String): List<String> {
        val directory = stateDirectory ?: return listOf("", "", "")
        return (1..3).map { slot ->
            val file = slotNameFile(directory, fileName, slot)
            if (!file.isFile) "" else file.readText().take(SLOT_NAME_LIMIT)
        }
    }

    private fun writeSlotName(fileName: String, slot: Int, label: String) {
        val directory = stateDirectory ?: return
        directory.mkdirs()
        val file = slotNameFile(directory, fileName, slot)
        if (label.isBlank()) {
            file.delete()
            return
        }
        file.writeText(label)
    }

    fun onSaveState() {
        if (askIfReplacing(ReplaceAsk.Save)) return
        writeSelectedState()
    }

    private fun writeSelectedState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val slot = _uiState.value.selectedSlot
        val bytes = synchronized(coreLock) { machine?.exportState() } ?: return
        if (bytes.isEmpty()) return
        directory.mkdirs()
        stateFile(directory, name, slot).writeBytes(bytes)
    }

    private fun askIfReplacing(ask: ReplaceAsk): Boolean {
        if (_uiState.value.replaceAsk != null) return true
        if (!slotHasState()) return false
        val slot = _uiState.value.selectedSlot
        val raw = _uiState.value.slotNames.getOrElse(slot - 1) { "" }.trim()
        _uiState.update {
            it.copy(replaceAsk = ask, replaceLabel = raw.ifBlank { slot.toString() })
        }
        return true
    }

    private fun slotHasState(): Boolean {
        val name = _uiState.value.loadedFileName ?: return false
        val directory = stateDirectory ?: return false
        return stateFile(directory, name, _uiState.value.selectedSlot).isFile
    }

    private fun saveCartridge() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val bytes = synchronized(coreLock) { machine?.exportCartridge() } ?: return
        if (bytes.isEmpty()) return
        directory.mkdirs()
        cartridgeFile(directory, name).writeBytes(bytes)
    }

    private fun cartridgeBytes(name: String): ByteArray? {
        val directory = stateDirectory ?: return null
        val file = cartridgeFile(directory, name)
        if (!file.isFile) return null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        return bytes
    }

    fun onLoadState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val file = stateFile(directory, name, _uiState.value.selectedSlot)
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

    fun onReplaceConfirmed(): Boolean {
        val ask = _uiState.value.replaceAsk ?: return false
        _uiState.update { it.copy(replaceAsk = null, replaceLabel = "") }
        if (ask == ReplaceAsk.Leave) {
            finishLeave(writeState = true)
            return true
        }
        writeSelectedState()
        return false
    }

    fun onReplaceRejected(): Boolean {
        val ask = _uiState.value.replaceAsk ?: return false
        _uiState.update { it.copy(replaceAsk = null, replaceLabel = "") }
        if (ask == ReplaceAsk.Leave) {
            finishLeave(writeState = false)
            return true
        }
        return false
    }

    fun onLeave(): Boolean {
        if (askIfReplacing(ReplaceAsk.Leave)) return false
        finishLeave(writeState = true)
        return true
    }

    private fun finishLeave(writeState: Boolean) {
        ticker?.cancel()
        ticker = null
        paused = true
        if (writeState) writeSelectedState()
        saveCartridge()
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
        const val SLOT_NAME_LIMIT = 50

        fun stateFile(directory: File, name: String, slot: Int = 1): File {
            val safe = name.replace("/", "_").replace("\\", "_")
            val fileName = if (slot == 1) safe else "$safe.$slot"
            return File(directory, fileName)
        }

        fun cartridgeFile(directory: File, name: String): File {
            return File(directory, stateFile(directory, name).name + ".sav")
        }

        fun slotNameFile(directory: File, name: String, slot: Int): File {
            return File(directory, stateFile(directory, name, slot).name + ".name")
        }
    }
}
