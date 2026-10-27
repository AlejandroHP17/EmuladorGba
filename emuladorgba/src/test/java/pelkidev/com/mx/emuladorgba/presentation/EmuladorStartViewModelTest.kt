package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladorgba.data.GbaCore
import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.GbaMachine

class EmuladorStartViewModelTest {

    @Test
    fun otherExtension_doesNotLoadAndAsksForGba() {
        val viewModel = newViewModel()

        viewModel.onFileChosen("notas.txt") { byteArrayOf(1) }

        assertNull(viewModel.uiState.value.loadedFileName)
        assertNull(viewModel.loadedRom)
        assertNull(viewModel.frame.value)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun otherExtension_keepsTheTitleAlreadyShown() {
        val viewModel = newViewModel()
        val rom = ByteArray(0xA0 + 12)
        "ZELDA       ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.frame.value

        viewModel.onFileChosen("notas.txt") { byteArrayOf(9) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.frame.value === frame)
        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.frame.value!!.pixels)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_keepsTheBytesForThisSession() {
        val viewModel = newViewModel()
        val rom = byteArrayOf(4, 5, 6)

        viewModel.onFileChosen("otro.GBA") { rom }

        assertEquals("otro.GBA", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertEquals(
            GbaFrame.WIDTH * GbaFrame.HEIGHT,
            viewModel.frame.value!!.pixels.size,
        )
        assertFalse(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun gbaFile_buildsTheFrameFromTheRomBytes() {
        val viewModel = newViewModel()
        val rom = ByteArray(0xA0 + 12)
        "TETRIS      ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)

        viewModel.onFileChosen("  otro.gba  ") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.frame.value!!.pixels)
    }

    @Test
    fun unreadGba_keepsThePreviousSession() {
        val viewModel = newViewModel()
        val rom = byteArrayOf(7)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.frame.value

        viewModel.onFileChosen("otro.gba") { null }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.frame.value === frame)
    }

    @Test
    fun withoutAGba_thereIsNoFrame() {
        val viewModel = newViewModel()

        assertNull(viewModel.frame.value)
    }

    @Test
    fun anotherGba_replacesTheFrame() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        val first = viewModel.frame.value

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }

        assertNotNull(first)
        assertNotNull(viewModel.frame.value)
        assertTrue(viewModel.frame.value !== first)
    }

    @Test
    fun loadedGba_showsTheMachineFrame() {
        val viewModel = newViewModel()
        val rom = paintRedAtOrigin()

        viewModel.onFileChosen("juego.gba") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.frame.value!!.pixels)
        assertEquals(BLACK, viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun skippedInstruction_replacesTheShownFrame() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") {
            words(
                0xE3A01406.toInt(),
                0xE3A0201F.toInt(),
                0xE1C120B0.toInt(),
                0xFFFFFFFF.toInt(),
                0xE1C120B2.toInt(),
            )
        }

        viewModel.onMachineTick()

        val frame = viewModel.frame.value!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, frame.pixels.size)
        assertEquals(RED, frame.pixel(0, 0))
        assertEquals(RED, frame.pixel(1, 0))
    }

    @Test
    fun tiledBackground_replacesTheShownFrame() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") {
            words(
                0xE3A00404.toInt(),
                0xE3A01C01.toInt(),
                0xE1C010B0.toInt(),
                0xE1C010B8.toInt(),
                0xE3A03405.toInt(),
                0xE3A0201F.toInt(),
                0xE1C320B2.toInt(),
                0xE3A04406.toInt(),
                0xE3A02001.toInt(),
                0xE1C420B0.toInt(),
            )
        }
        assertEquals(BLACK, viewModel.frame.value!!.pixel(0, 0))

        viewModel.onMachineTick()

        val shown = viewModel.frame.value!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, shown.pixels.size)
        assertEquals(RED, shown.pixel(0, 0))
        assertEquals(BLACK, shown.pixel(1, 0))
        viewModel.onMachineTick()
        assertTrue(viewModel.frame.value === shown)
    }

    @Test
    fun whenTheMachinePaints_theShownFrameIsReplaced() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.onMachineTick()

        assertEquals(RED, viewModel.frame.value!!.pixel(0, 0))
        val painted = viewModel.frame.value
        viewModel.onMachineTick()
        assertTrue(viewModel.frame.value === painted)
    }

    @Test
    fun leaving_stopsTheAdvance() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.clear()
        Thread.sleep(350)
        viewModel.onMachineTick()

        assertEquals(BLACK, viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun object_replacesTheShownFrame() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }
        assertEquals(BLACK, viewModel.frame.value!!.pixel(8, 0))

        viewModel.onMachineTick()

        val shown = viewModel.frame.value!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, shown.pixels.size)
        assertEquals(RED, shown.pixel(0, 0))
        assertEquals(GREEN, shown.pixel(8, 0))
        viewModel.onMachineTick()
        assertTrue(viewModel.frame.value === shown)
    }

    @Test
    fun waiting_replacesThePictureWithoutAControl() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { movingPicture() }
        val initial = viewModel.frame.value!!

        viewModel.onMachineTick()
        val first = viewModel.frame.value!!
        viewModel.onMachineTick()
        val second = viewModel.frame.value!!

        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, second.pixels.size)
        assertFalse(initial.pixels.contentEquals(first.pixels))
        assertFalse(first.pixels.contentEquals(second.pixels))
    }

    @Test
    fun anotherGba_dropsThePreviousMovement() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { movingPicture() }
        viewModel.onMachineTick()
        viewModel.onMachineTick()
        val moving = viewModel.frame.value!!

        viewModel.onFileChosen("otro.gba") { byteArrayOf(1) }
        viewModel.onMachineTick()

        val shown = viewModel.frame.value!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, shown.pixels.size)
        assertFalse(moving.pixels.contentEquals(shown.pixels))
    }

    @Test
    fun leaving_stopsTheMovement() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { movingPicture() }
        viewModel.onMachineTick()
        val moving = viewModel.frame.value

        viewModel.clear()
        viewModel.onMachineTick()

        assertTrue(viewModel.frame.value === moving)
    }

    @Test
    fun anotherGba_dropsThePreviousObject() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { byteArrayOf(1) }

        assertEquals(BLACK, viewModel.frame.value!!.pixel(8, 0))
        assertEquals(BLACK, viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun leaving_stopsTheObject() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }

        viewModel.clear()
        viewModel.onMachineTick()

        assertEquals(BLACK, viewModel.frame.value!!.pixel(8, 0))
    }

    @Test
    fun whenTheProgramWritesSound_theSamplesArePublished() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }

        assertEquals(0, viewModel.uiState.value.samples.size)
        viewModel.onMachineTick()

        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44), viewModel.uiState.value.samples)
    }

    @Test
    fun whenTheProgramDoesNotWriteSound_silenceIsPublished() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun repeatedSound_isPublishedAgain() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { loopingDirectChannel() }
        viewModel.onMachineTick()
        viewModel.onMachineTick()
        val first = viewModel.uiState.value.samples

        viewModel.onMachineTick()

        val second = viewModel.uiState.value.samples
        assertTrue(first.isNotEmpty())
        assertTrue(first !== second)
        assertArrayEquals(first, second)
    }

    @Test
    fun pcm16_keepsTheSignedSample() {
        assertEquals(0, gbaPcm16(byteArrayOf()).size)
        assertArrayEquals(byteArrayOf(0x00, 0x11), gbaPcm16(byteArrayOf(0x11)))
        assertArrayEquals(byteArrayOf(0x00, 0x00), gbaPcm16(byteArrayOf(0x00)))
    }

    @Test
    fun laterAdvanceWithoutWrites_publishesSilence() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun anotherGba_startsInSilence() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { paintRedAtOrigin() }

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun anotherGba_replacesThePreviousSound() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { otherDirectChannel() }
        assertEquals(0, viewModel.uiState.value.samples.size)
        viewModel.onMachineTick()

        assertArrayEquals(byteArrayOf(0x7F, 0, 0, 0), viewModel.uiState.value.samples)
    }

    @Test
    fun leaving_cutsTheSound() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.clear()
        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun withoutARom_thereIsSilence() {
        val viewModel = newViewModel()

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun heldControl_reachesTheProgram() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)

        viewModel.onMachineTick()

        assertEquals(pixelAfter(setOf(GbaButton.A)), viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun twoHeldControls_bothReachTheProgram() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onControlChanged(GbaButton.B, true)

        viewModel.onMachineTick()

        assertEquals(
            pixelAfter(setOf(GbaButton.A, GbaButton.B)),
            viewModel.frame.value!!.pixel(0, 0),
        )
    }

    @Test
    fun releasedControl_noLongerReachesTheProgram() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onMachineTick()

        viewModel.onControlChanged(GbaButton.A, false)
        viewModel.onMachineTick()

        assertEquals(pixelAfter(emptySet()), viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun pause_releasesHeldControls() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onMachineTick()

        viewModel.onAppStopped()
        viewModel.onAppStarted()
        viewModel.onMachineTick()

        assertEquals(pixelAfter(emptySet()), viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun heldControl_doesNotCarryToTheNextRom() {
        val viewModel = newViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)

        viewModel.onFileChosen("otro.gba") { readControls() }
        viewModel.onMachineTick()

        assertEquals(pixelAfter(emptySet()), viewModel.frame.value!!.pixel(0, 0))
    }

    @Test
    fun withoutARom_aControlDoesNothing() {
        val viewModel = newViewModel()

        viewModel.onControlChanged(GbaButton.A, true)

        assertNull(viewModel.frame.value)
    }

    @Test
    fun withoutARom_theMachineDoesNotRun() {
        val viewModel = newViewModel()

        repeat(3) { viewModel.onMachineTick() }
        viewModel.onFileChosen("notas.txt") { byteArrayOf(1) }
        viewModel.onMachineTick()

        assertNull(viewModel.loadedRom)
        assertNull(viewModel.frame.value)
    }

    @Test
    fun coreThatCannotLoad_keepsThePreviousSession() {
        var failing = false
        val viewModel = EmuladorStartViewModel(
            startCore = { rom ->
                check(!failing) { "no carga" }
                MachineCore(GbaMachine(rom))
            },
            autoAdvance = false,
        )
        val rom = byteArrayOf(7)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.frame.value

        failing = true
        viewModel.onFileChosen("roto.gba") { byteArrayOf(1) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.frame.value === frame)
    }

    @Test
    fun leave_stopsTheCoreAndKeepsTheLastFrame() {
        val cores = mutableListOf<MachineCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { rom -> MachineCore(GbaMachine(rom)).also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        viewModel.onMachineTick()
        val frame = viewModel.frame.value

        viewModel.onLeave()
        viewModel.onMachineTick()

        assertTrue(cores[0].closed)
        assertTrue(viewModel.frame.value === frame)
    }

    @Test
    fun anotherGba_closesThePreviousCore() {
        val cores = mutableListOf<MachineCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { rom -> MachineCore(GbaMachine(rom)).also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }
        viewModel.clear()

        assertTrue(cores[0].closed)
        assertTrue(cores[1].closed)
    }

    @Test
    fun save_replacesTheStateOfThatFileName() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        viewModel.onSaveState()

        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
        assertEquals(ReplaceAsk.Save, viewModel.uiState.value.replaceAsk)
        assertEquals("1", viewModel.uiState.value.replaceLabel)
        assertFalse(File(directory, "otro.gba").exists())
    }

    @Test
    fun emptySlot_savesOnSaveAndLeaveWithoutAsking() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 2
        viewModel.onSaveState()

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(2), File(directory, "juego.gba").readBytes())

        viewModel.onSlotSelected(3)
        cores[0].token = 5
        assertTrue(viewModel.onLeave())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(5), File(directory, "juego.gba.3").readBytes())
        assertArrayEquals(byteArrayOf(2), File(directory, "juego.gba").readBytes())
    }

    @Test
    fun save_asksWithTheSlotName() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        viewModel.onSlotNamed("Bosque")
        cores[0].token = 1
        viewModel.onSaveState()
        cores[0].token = 2
        viewModel.onSaveState()

        assertEquals(ReplaceAsk.Save, viewModel.uiState.value.replaceAsk)
        assertEquals("Bosque", viewModel.uiState.value.replaceLabel)
        assertArrayEquals(byteArrayOf(1), File(directory, "juego.gba").readBytes())
    }

    @Test
    fun confirm_replacesOnlyTheChosenSlotAndKeepsPlaying() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)
        cores[0].token = 4
        viewModel.onSaveState()
        cores[0].token = 7
        viewModel.onSaveState()

        assertFalse(viewModel.onReplaceConfirmed())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(byteArrayOf(7), File(directory, "juego.gba.2").readBytes())
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
    }

    @Test
    fun confirm_afterLeave_replacesTheSlotAndLeaves() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        assertFalse(viewModel.onLeave())
        val frame = viewModel.frame.value

        assertTrue(viewModel.onReplaceConfirmed())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba").readBytes())
        viewModel.onMachineTick()
        assertTrue(viewModel.frame.value === frame)
    }

    @Test
    fun reject_keepsTheChosenSlotAndKeepsPlaying() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)
        cores[0].token = 4
        viewModel.onSaveState()
        cores[0].token = 7
        viewModel.onSaveState()

        assertFalse(viewModel.onReplaceRejected())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba.2").readBytes())
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
    }

    @Test
    fun reject_afterLeave_keepsTheSlotAndLeaves() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        cores[0].cartridge = byteArrayOf(3)
        viewModel.onSaveState()
        cores[0].token = 4
        cores[0].cartridge = byteArrayOf(8)
        assertFalse(viewModel.onLeave())
        val frame = viewModel.frame.value

        assertTrue(viewModel.onReplaceRejected())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
        assertArrayEquals(byteArrayOf(8), File(directory, "juego.gba.sav").readBytes())
        viewModel.onMachineTick()
        assertTrue(viewModel.frame.value === frame)
    }

    @Test
    fun leave_replacesTheStateOfThatFileName() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4

        assertFalse(viewModel.onLeave())

        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
        assertEquals(ReplaceAsk.Leave, viewModel.uiState.value.replaceAsk)
        assertEquals("1", viewModel.uiState.value.replaceLabel)
    }

    @Test
    fun leave_writesTheCartridgeApartFromTheState() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 4
        cores[0].cartridge = byteArrayOf(8, 8)

        viewModel.onLeave()

        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba").readBytes())
        assertArrayEquals(byteArrayOf(8, 8), File(directory, "juego.gba.sav").readBytes())
    }

    @Test
    fun anotherFile_writesThePreviousCartridge() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].cartridge = byteArrayOf(2)

        viewModel.onFileChosen("otro.gba") { byteArrayOf(3) }

        assertArrayEquals(byteArrayOf(2), File(directory, "juego.gba.sav").readBytes())
        assertFalse(File(directory, "otro.gba.sav").exists())
    }

    @Test
    fun open_loadsTheCartridgeOfThatFileBeforePlay() {
        val directory = createTempDirectory("gba-state").toFile()
        File(directory, "juego.gba.sav").writeBytes(byteArrayOf(9))
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)

        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }

        assertArrayEquals(byteArrayOf(9), cores[0].loadedCartridge)
        assertNotNull(viewModel.frame.value)
    }

    @Test
    fun open_withoutCartridge_doesNotLoadOne() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)

        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }

        assertNull(cores[0].loadedCartridge)
    }

    @Test
    fun reopen_usesTheLatestCartridgeOfThatFile() {
        val directory = createTempDirectory("gba-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.gba") { byteArrayOf(1) }
        firstCores[0].cartridge = byteArrayOf(1)
        first.onLeave()

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(directory, secondCores)
        second.onFileChosen("juego.gba") { byteArrayOf(1) }
        assertArrayEquals(byteArrayOf(1), secondCores[0].loadedCartridge)
        secondCores[0].cartridge = byteArrayOf(5)
        assertFalse(second.onLeave())

        assertEquals(ReplaceAsk.Leave, second.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(1), File(directory, "juego.gba.sav").readBytes())

        val thirdCores = mutableListOf<RememberingCore>()
        val third = viewModelWithStates(directory, thirdCores)
        third.onFileChosen("otro.gba") { byteArrayOf(2) }
        assertNull(thirdCores[0].loadedCartridge)
    }

    @Test
    fun open_doesNotLoadAnotherFilesCartridge() {
        val directory = createTempDirectory("gba-state").toFile()
        File(directory, "juego.gba.sav").writeBytes(byteArrayOf(9))
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }

        assertNull(cores[0].loadedCartridge)
    }

    @Test
    fun leave_withoutAFile_doesNotCreateState() {
        val directory = createTempDirectory("gba-state").toFile()
        val kept = File(directory, "juego.gba")
        kept.writeBytes(byteArrayOf(3))
        val viewModel = viewModelWithStates(directory, mutableListOf())

        viewModel.onLeave()

        assertArrayEquals(byteArrayOf(3), kept.readBytes())
        assertEquals(listOf("juego.gba"), directory.list()?.toList())
    }

    @Test
    fun load_withoutState_keepsTheFrame() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        val frame = viewModel.frame.value

        viewModel.onLoadState()

        assertTrue(viewModel.frame.value === frame)
        assertNull(cores[0].imported)
    }

    @Test
    fun load_doesNotApplyAnotherFile() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 7
        viewModel.onSaveState()

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }
        val frame = viewModel.frame.value
        viewModel.onLoadState()

        assertTrue(viewModel.frame.value === frame)
        assertNull(cores[1].imported)
    }

    @Test
    fun reopenedEmulator_loadsTheStateOfTheSameFile() {
        val directory = createTempDirectory("gba-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.gba") { byteArrayOf(1) }
        firstCores[0].token = 6
        first.onSaveState()

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(directory, secondCores)
        second.onFileChosen("juego.gba") { byteArrayOf(1) }
        second.onLoadState()

        assertArrayEquals(byteArrayOf(6), secondCores[0].imported)
        assertEquals(6, second.frame.value!!.pixel(0, 0))
    }

    @Test
    fun slots_keepSeparateStatesAndReplaceOnlyTheChosenOne() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        assertEquals(1, viewModel.uiState.value.selectedSlot)
        cores[0].token = 9
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)
        cores[0].token = 4
        viewModel.onSaveState()
        cores[0].token = 7
        viewModel.onSaveState()
        viewModel.onSlotSelected(4)

        assertEquals(2, viewModel.uiState.value.selectedSlot)
        assertEquals(ReplaceAsk.Save, viewModel.uiState.value.replaceAsk)
        assertEquals("2", viewModel.uiState.value.replaceLabel)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.gba").readBytes())
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba.2").readBytes())
        assertFalse(File(directory, "juego.gba.3").exists())

        viewModel.onSlotSelected(1)
        viewModel.onLoadState()

        assertArrayEquals(byteArrayOf(9), cores[0].imported)
        assertEquals(9, viewModel.frame.value!!.pixel(0, 0))
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba.2").readBytes())
    }

    @Test
    fun load_emptySlot_keepsTheFrame() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        cores[0].token = 9
        viewModel.onSaveState()
        val frame = viewModel.frame.value

        viewModel.onSlotSelected(3)
        viewModel.onLoadState()

        assertTrue(viewModel.frame.value === frame)
        assertNull(cores[0].imported)
    }

    @Test
    fun load_doesNotApplyAnotherFilesSlot() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        viewModel.onSlotSelected(2)
        cores[0].token = 7
        viewModel.onSaveState()

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }
        assertEquals(1, viewModel.uiState.value.selectedSlot)
        viewModel.onSlotSelected(2)
        val frame = viewModel.frame.value
        viewModel.onLoadState()

        assertTrue(viewModel.frame.value === frame)
        assertNull(cores[1].imported)
    }

    @Test
    fun leave_writesTheSelectedSlot() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        viewModel.onSlotSelected(2)
        cores[0].token = 4

        viewModel.onLeave()

        assertArrayEquals(byteArrayOf(4), File(directory, "juego.gba.2").readBytes())
        assertFalse(File(directory, "juego.gba").exists())
    }

    @Test
    fun slotNames_belongToThatFileAndDoNotReplaceTheMoment() {
        val directory = createTempDirectory("gba-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        val frame = viewModel.frame.value

        viewModel.onSlotNamed("jefe")

        assertEquals(listOf("jefe", "", ""), viewModel.uiState.value.slotNames)
        assertEquals("jefe", File(directory, "juego.gba.name").readText())
        assertTrue(viewModel.frame.value === frame)
        assertFalse(File(directory, "juego.gba").exists())

        viewModel.onSlotNamed("a".repeat(60))

        assertEquals("a".repeat(50), viewModel.uiState.value.slotNames[0])
        assertEquals("a".repeat(50), File(directory, "juego.gba.name").readText())

        viewModel.onSlotSelected(2)
        viewModel.onSlotNamed("bosque")
        cores[0].token = 9
        viewModel.onSaveState()
        val saved = File(directory, "juego.gba.2").readBytes()
        viewModel.onSlotNamed("bosque nuevo")

        assertEquals("a".repeat(50), viewModel.uiState.value.slotNames[0])
        assertEquals("bosque nuevo", viewModel.uiState.value.slotNames[1])
        assertArrayEquals(saved, File(directory, "juego.gba.2").readBytes())
        viewModel.onLoadState()
        assertArrayEquals(byteArrayOf(9), cores[0].imported)
        assertEquals(9, viewModel.frame.value!!.pixel(0, 0))

        viewModel.onSlotNamed("   ")

        assertTrue(viewModel.uiState.value.slotNames[1].isBlank())
        assertFalse(File(directory, "juego.gba.2.name").exists())
        assertArrayEquals(saved, File(directory, "juego.gba.2").readBytes())

        val reopened = viewModelWithStates(directory, mutableListOf())
        reopened.onFileChosen("juego.gba") { byteArrayOf(1) }

        assertEquals("a".repeat(50), reopened.uiState.value.slotNames[0])
        assertEquals("", reopened.uiState.value.slotNames[1])
        assertEquals("", reopened.uiState.value.slotNames[2])

        reopened.onFileChosen("otro.gba") { byteArrayOf(2) }

        assertEquals(listOf("", "", ""), reopened.uiState.value.slotNames)
        assertFalse(File(directory, "otro.gba.name").exists())
        assertEquals("a".repeat(50), File(directory, "juego.gba.name").readText())
    }

    @Test
    fun threeQuickTapsOnADirection_doNotStayHeld() {
        val clock = TestClock()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithClock(cores, clock)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }

        clock.time = 0
        viewModel.onControlChanged(GbaButton.UP, true)
        assertEquals(setOf(GbaButton.UP), cores[0].held)

        viewModel.onControlChanged(GbaButton.UP, false)
        tap(viewModel, clock, GbaButton.LEFT, 0, 100, 200)

        assertEquals(emptySet<GbaButton>(), cores[0].held)
        assertEquals(emptySet<GbaButton>(), viewModel.uiState.value.latched)
    }

    @Test
    fun threeQuickTaps_stayHeldAfterTheFingerLifts() {
        val clock = TestClock()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithClock(cores, clock)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        tap(viewModel, clock, GbaButton.A, 0, 100, 200)

        assertEquals(setOf(GbaButton.A), cores[0].held)
        assertEquals(setOf(GbaButton.A), viewModel.uiState.value.latched)
    }

    @Test
    fun anotherTap_releasesTheLatchedControl() {
        val clock = TestClock()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithClock(cores, clock)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        tap(viewModel, clock, GbaButton.A, 0, 100, 200)

        clock.time = 300
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onControlChanged(GbaButton.A, false)

        assertEquals(emptySet<GbaButton>(), cores[0].held)
        assertEquals(emptySet<GbaButton>(), viewModel.uiState.value.latched)
    }

    @Test
    fun anotherGba_releasesTheLatchedControl() {
        val clock = TestClock()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithClock(cores, clock)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        tap(viewModel, clock, GbaButton.A, 0, 100, 200)

        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }

        assertEquals(emptySet<GbaButton>(), cores[1].held)
        assertEquals(emptySet<GbaButton>(), viewModel.uiState.value.latched)
    }

    @Test
    fun leavingTheApp_releasesTheLatchedControl() {
        val clock = TestClock()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithClock(cores, clock)
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }
        tap(viewModel, clock, GbaButton.A, 0, 100, 200)

        viewModel.onAppStopped()

        assertEquals(emptySet<GbaButton>(), cores[0].held)
        assertEquals(emptySet<GbaButton>(), viewModel.uiState.value.latched)

        viewModel.onAppStarted()

        assertEquals(emptySet<GbaButton>(), cores[0].held)
        assertEquals(emptySet<GbaButton>(), viewModel.uiState.value.latched)
    }

    @Test
    fun fastForward_runsTwoFramesAndReturnsToOne() {
        val cores = mutableListOf<RememberingCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { RememberingCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.gba") { byteArrayOf(1) }

        viewModel.onMachineTick()

        assertEquals(1, cores[0].steps)

        viewModel.onFastForwardChanged(true)
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onMachineTick()

        assertEquals(3, cores[0].steps)
        assertEquals(setOf(GbaButton.A), cores[0].held)

        viewModel.onFastForwardChanged(false)
        viewModel.onMachineTick()

        assertEquals(4, cores[0].steps)

        viewModel.onFastForwardChanged(true)
        viewModel.onFileChosen("otro.gba") { byteArrayOf(2) }
        viewModel.onMachineTick()

        assertFalse(viewModel.uiState.value.fastForward)
        assertEquals(1, cores[1].steps)

        viewModel.onFastForwardChanged(true)
        viewModel.onAppStopped()
        viewModel.onAppStarted()
        viewModel.onMachineTick()

        assertFalse(viewModel.uiState.value.fastForward)
        assertEquals(2, cores[1].steps)
    }

    private class TestClock {
        var time = 0L
    }

    private fun viewModelWithClock(
        cores: MutableList<RememberingCore>,
        clock: TestClock,
    ) = EmuladorStartViewModel(
        startCore = { RememberingCore().also(cores::add) },
        autoAdvance = false,
        clock = { clock.time },
    )

    private fun tap(
        viewModel: EmuladorStartViewModel,
        clock: TestClock,
        button: GbaButton,
        vararg starts: Long,
    ) {
        for (at in starts) {
            clock.time = at
            viewModel.onControlChanged(button, true)
            viewModel.onControlChanged(button, false)
        }
    }

    private fun viewModelWithStates(directory: File, cores: MutableList<RememberingCore>) =
        EmuladorStartViewModel(
            startCore = { RememberingCore().also(cores::add) },
            autoAdvance = false,
            stateDirectory = directory,
        )

    private class RememberingCore : GbaCore {
        var token: Byte = 1
        var cartridge: ByteArray = byteArrayOf()
        var loadedCartridge: ByteArray? = null
            private set
        var imported: ByteArray? = null
            private set
        private var shown: Byte = 1
        var steps = 0
        var held: Set<GbaButton> = emptySet()
            private set

        override fun step() {
            steps += 1
        }
        override fun frame(): GbaFrame {
            val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT)
            pixels[0] = shown.toInt() and 0xFF
            return GbaFrame(pixels)
        }
        override fun samples(): ByteArray = byteArrayOf()
        override fun hold(buttons: Set<GbaButton>) {
            held = buttons
        }
        override fun exportState(): ByteArray = byteArrayOf(token)
        override fun exportCartridge(): ByteArray = cartridge.copyOf()
        override fun importCartridge(savedata: ByteArray): Boolean {
            loadedCartridge = savedata.copyOf()
            return true
        }
        override fun importState(state: ByteArray): Boolean {
            imported = state.copyOf()
            shown = state.first()
            return true
        }
        override fun close() = Unit
    }

    private fun newViewModel() = EmuladorStartViewModel(
        startCore = { rom -> MachineCore(GbaMachine(rom)) },
        autoAdvance = false,
    )

    private class MachineCore(private val machine: GbaMachine) : GbaCore {
        var closed = false
            private set

        override fun step() = machine.step()
        override fun frame(): GbaFrame = machine.frame()
        override fun samples(): ByteArray = machine.samples()
        override fun hold(buttons: Set<GbaButton>) = machine.hold(buttons)
        override fun close() {
            closed = true
        }
    }

    private fun ViewModel.clear() {
        val clear = ViewModel::class.java.getDeclaredMethod("onCleared")
        clear.isAccessible = true
        clear.invoke(this)
    }

    private fun readControls(): ByteArray = words(
        0xE3A00000.toInt(),
        0xE3A01404.toInt(),
        0xE2811C01.toInt(),
        0xE2811030.toInt(),
        0xE3A03406.toInt(),
        0xE28F4001.toInt(),
        0xE12FFF14.toInt(),
    ) + byteArrayOf(0x0A, 0x5A, 0x1A, 0x52, 0xFC.toByte(), 0xE7.toByte())

    private fun pixelAfter(buttons: Set<GbaButton>): Int {
        val machine = GbaMachine(readControls())
        machine.hold(buttons)
        machine.step()
        return machine.frame().pixel(0, 0)
    }

    private fun loopingDirectChannel(): ByteArray = words(
        0xE59F200C.toInt(),
        0xE3A01404.toInt(),
        0xE28110A0.toInt(),
        0xE5812000.toInt(),
        0xEAFFFFFD.toInt(),
        0x44332211,
    )

    private fun otherDirectChannel(): ByteArray = words(
        0xE59F2008.toInt(),
        0xE3A01404.toInt(),
        0xE28110A0.toInt(),
        0xE5812000.toInt(),
        0x0000007F,
    )

    private fun directChannelProgram(): ByteArray = words(
        0xE59F2008.toInt(),
        0xE3A01404.toInt(),
        0xE28110A0.toInt(),
        0xE5812000.toInt(),
        0x44332211,
    )

    private fun movingPicture(): ByteArray = words(
        0xE3A04406.toInt(),
        0xE3A02000.toInt(),
        0xE2822001.toInt(),
        0xE1C420B0.toInt(),
        0xEAFFFFFC.toInt(),
    )

    private fun objectOverBackground(): ByteArray = words(
        0xE3A00404.toInt(),
        0xE3A01C11.toInt(),
        0xE1C010B0.toInt(),
        0xE3A01C01.toInt(),
        0xE1C010B8.toInt(),
        0xE3A03405.toInt(),
        0xE3A0201F.toInt(),
        0xE1C320B2.toInt(),
        0xE3A02FF8.toInt(),
        0xE2833C02.toInt(),
        0xE1C320B2.toInt(),
        0xE3A04406.toInt(),
        0xE3A02001.toInt(),
        0xE1C420B0.toInt(),
        0xE2844801.toInt(),
        0xE2844020.toInt(),
        0xE1C420B0.toInt(),
        0xE3A05407.toInt(),
        0xE3A02008.toInt(),
        0xE1C520B2.toInt(),
        0xE3A02001.toInt(),
        0xE1C520B4.toInt(),
    )

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
        const val GREEN = 0xFF00FF00.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
