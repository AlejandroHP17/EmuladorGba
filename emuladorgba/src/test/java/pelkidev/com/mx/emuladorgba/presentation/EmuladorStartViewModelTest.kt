package pelkidev.com.mx.emuladorgba.presentation

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame
import pelkidev.com.mx.emuladorgba.domain.GbaMachine

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
    fun otherExtension_keepsTheTitleAlreadyShown() {
        val viewModel = EmuladorStartViewModel()
        val rom = ByteArray(0xA0 + 12)
        "ZELDA       ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)
        viewModel.onFileChosen("juego.gba") { rom }
        val frame = viewModel.uiState.value.frame

        viewModel.onFileChosen("notas.txt") { byteArrayOf(9) }

        assertEquals("juego.gba", viewModel.uiState.value.loadedFileName)
        assertArrayEquals(rom, viewModel.loadedRom)
        assertTrue(viewModel.uiState.value.frame === frame)
        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
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
    fun gbaFile_buildsTheFrameFromTheRomBytes() {
        val viewModel = EmuladorStartViewModel()
        val rom = ByteArray(0xA0 + 12)
        "TETRIS      ".toByteArray(Charsets.US_ASCII).copyInto(rom, destinationOffset = 0xA0)

        viewModel.onFileChosen("  otro.gba  ") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
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

    @Test
    fun loadedGba_showsTheMachineFrame() {
        val viewModel = EmuladorStartViewModel()
        val rom = paintRedAtOrigin()

        viewModel.onFileChosen("juego.gba") { rom }

        assertArrayEquals(GbaMachine(rom).frame().pixels, viewModel.uiState.value.frame!!.pixels)
        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun skippedInstruction_replacesTheShownFrame() {
        val viewModel = EmuladorStartViewModel()
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

        val frame = viewModel.uiState.value.frame!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, frame.pixels.size)
        assertEquals(RED, frame.pixel(0, 0))
        assertEquals(RED, frame.pixel(1, 0))
    }

    @Test
    fun tiledBackground_replacesTheShownFrame() {
        val viewModel = EmuladorStartViewModel()
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
        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(0, 0))

        viewModel.onMachineTick()

        val shown = viewModel.uiState.value.frame!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, shown.pixels.size)
        assertEquals(RED, shown.pixel(0, 0))
        assertEquals(BLACK, shown.pixel(1, 0))
        viewModel.onMachineTick()
        assertTrue(viewModel.uiState.value.frame === shown)
    }

    @Test
    fun whenTheMachinePaints_theShownFrameIsReplaced() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.onMachineTick()

        assertEquals(RED, viewModel.uiState.value.frame!!.pixel(0, 0))
        val painted = viewModel.uiState.value.frame
        viewModel.onMachineTick()
        assertTrue(viewModel.uiState.value.frame === painted)
    }

    @Test
    fun leaving_stopsTheAdvance() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.clear()
        Thread.sleep(350)
        viewModel.onMachineTick()

        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun object_replacesTheShownFrame() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }
        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(8, 0))

        viewModel.onMachineTick()

        val shown = viewModel.uiState.value.frame!!
        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, shown.pixels.size)
        assertEquals(RED, shown.pixel(0, 0))
        assertEquals(GREEN, shown.pixel(8, 0))
        viewModel.onMachineTick()
        assertTrue(viewModel.uiState.value.frame === shown)
    }

    @Test
    fun waiting_replacesThePictureWithoutAControl() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { movingPicture() }
        val initial = viewModel.uiState.value.frame!!

        viewModel.onMachineTick()
        val first = viewModel.uiState.value.frame!!
        viewModel.onMachineTick()
        val second = viewModel.uiState.value.frame!!

        assertEquals(GbaFrame.WIDTH * GbaFrame.HEIGHT, second.pixels.size)
        assertFalse(initial.pixels.contentEquals(first.pixels))
        assertFalse(first.pixels.contentEquals(second.pixels))
    }

    @Test
    fun anotherGba_dropsThePreviousObject() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { byteArrayOf(1) }

        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(8, 0))
        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun leaving_stopsTheObject() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { objectOverBackground() }

        viewModel.clear()
        viewModel.onMachineTick()

        assertEquals(BLACK, viewModel.uiState.value.frame!!.pixel(8, 0))
    }

    @Test
    fun whenTheProgramWritesSound_theSamplesArePublished() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }

        assertEquals(0, viewModel.uiState.value.samples.size)
        viewModel.onMachineTick()

        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44), viewModel.uiState.value.samples)
    }

    @Test
    fun whenTheProgramDoesNotWriteSound_silenceIsPublished() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { paintRedAtOrigin() }

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun repeatedSound_isPublishedAgain() {
        val viewModel = EmuladorStartViewModel()
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
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun anotherGba_startsInSilence() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { paintRedAtOrigin() }

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun anotherGba_replacesThePreviousSound() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.onFileChosen("otro.gba") { otherDirectChannel() }
        assertEquals(0, viewModel.uiState.value.samples.size)
        viewModel.onMachineTick()

        assertArrayEquals(byteArrayOf(0x7F, 0, 0, 0), viewModel.uiState.value.samples)
    }

    @Test
    fun leaving_cutsTheSound() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { directChannelProgram() }
        viewModel.onMachineTick()

        viewModel.clear()
        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun withoutARom_thereIsSilence() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onMachineTick()

        assertEquals(0, viewModel.uiState.value.samples.size)
    }

    @Test
    fun heldControl_reachesTheProgram() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)

        viewModel.onMachineTick()

        assertEquals(pixelAfter(setOf(GbaButton.A)), viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun twoHeldControls_bothReachTheProgram() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onControlChanged(GbaButton.B, true)

        viewModel.onMachineTick()

        assertEquals(
            pixelAfter(setOf(GbaButton.A, GbaButton.B)),
            viewModel.uiState.value.frame!!.pixel(0, 0),
        )
    }

    @Test
    fun releasedControl_noLongerReachesTheProgram() {
        val viewModel = EmuladorStartViewModel()
        viewModel.onFileChosen("juego.gba") { readControls() }
        viewModel.onControlChanged(GbaButton.A, true)
        viewModel.onMachineTick()

        viewModel.onControlChanged(GbaButton.A, false)
        viewModel.onMachineTick()

        assertEquals(pixelAfter(emptySet()), viewModel.uiState.value.frame!!.pixel(0, 0))
    }

    @Test
    fun withoutARom_aControlDoesNothing() {
        val viewModel = EmuladorStartViewModel()

        viewModel.onControlChanged(GbaButton.A, true)

        assertNull(viewModel.uiState.value.frame)
    }

    @Test
    fun withoutARom_theMachineDoesNotRun() {
        val viewModel = EmuladorStartViewModel()

        repeat(3) { viewModel.onMachineTick() }
        viewModel.onFileChosen("notas.txt") { byteArrayOf(1) }
        viewModel.onMachineTick()

        assertNull(viewModel.loadedRom)
        assertNull(viewModel.uiState.value.frame)
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
