package pelkidev.com.mx.emuladorgba.data

import pelkidev.com.mx.emuladorgba.domain.GbaButton
import pelkidev.com.mx.emuladorgba.domain.GbaFrame

interface GbaCore {
    fun step()
    fun frame(): GbaFrame
    fun samples(): ByteArray
    fun hold(buttons: Set<GbaButton>)
    fun close()
}

class MgbaCore(rom: ByteArray) : GbaCore {
    private val pixels = IntArray(GbaFrame.WIDTH * GbaFrame.HEIGHT)
    private var handle: Long = nativeCreate(rom)

    init {
        check(handle != 0L) { "mGBA no pudo cargar el ROM" }
    }

    override fun step() {
        if (handle == 0L) return
        nativeRunFrame(handle, pixels)
    }

    override fun frame(): GbaFrame = GbaFrame(pixels)

    override fun samples(): ByteArray = byteArrayOf()

    override fun hold(buttons: Set<GbaButton>) = Unit

    override fun close() {
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(rom: ByteArray): Long
    private external fun nativeRunFrame(handle: Long, out: IntArray)
    private external fun nativeDestroy(handle: Long)

    private companion object {
        init {
            System.loadLibrary("emuladorgba_mgba")
        }
    }
}
